package com.miazheng.twitch.favorite;


import com.miazheng.twitch.db.FavoriteRecordRepository;
import com.miazheng.twitch.db.ItemRepository;
import com.miazheng.twitch.db.entity.FavoriteRecordEntity;
import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.db.entity.UserEntity;
import com.miazheng.twitch.external.TwitchService;
import com.miazheng.twitch.model.TypeGroupedItemList;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.stereotype.Service;


import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class FavoriteService {


    private static final Pattern GAME_ID = Pattern.compile("\\d{1,20}");
    private static final Pattern CHANNEL_URL = Pattern.compile("https://www\\.twitch\\.tv/([A-Za-z0-9_]{1,25})");


    private final ItemRepository itemRepository;
    private final FavoriteRecordRepository favoriteRecordRepository;
    private final TwitchService twitchService;


    public FavoriteService(ItemRepository itemRepository,
                           FavoriteRecordRepository favoriteRecordRepository,
                           TwitchService twitchService) {
        this.itemRepository = itemRepository;
        this.favoriteRecordRepository = favoriteRecordRepository;
        this.twitchService = twitchService;
    }


    // Concurrent requests are settled by the unique keys on items.twitch_id and
    // favorite_records(item_id, user_id). There is deliberately no transaction: under MySQL's
    // REPEATABLE READ, the re-read after losing the item insert would not see the winner's row.
    @CacheEvict(cacheNames = "recommend_items", key = "#user")
    public void setFavoriteItem(UserEntity user, ItemEntity item) {
        ItemEntity persistedItem = findOrSaveItem(item);
        FavoriteRecordEntity favoriteRecord = new FavoriteRecordEntity(null, user.id(), persistedItem.id(), Instant.now());
        try {
            favoriteRecordRepository.save(favoriteRecord);
        } catch (DbActionExecutionException e) {
            if (isDuplicateKey(e)) {
                throw new DuplicateFavoriteException();
            }
            throw e;
        }
    }


    private ItemEntity findOrSaveItem(ItemEntity requested) {
        ItemEntity persistedItem = itemRepository.findByTwitchId(requested.twitchId());
        if (persistedItem != null) {
            return persistedItem;
        }
        ItemEntity item = fetchFromTwitch(requested);
        try {
            return itemRepository.save(item);
        } catch (DbActionExecutionException e) {
            if (isDuplicateKey(e)) {
                return itemRepository.findByTwitchId(item.twitchId());
            }
            throw e;
        }
    }


    // Items are shared: what is stored is what every user who favorites the same item sees. So
    // the client only says which item it means, and the title, URL, thumbnail and channel come
    // from Twitch.
    private ItemEntity fetchFromTwitch(ItemEntity requested) {
        if (requested.twitchId() == null || requested.type() == null) {
            throw new UnknownItemException();
        }
        Optional<ItemEntity> item = switch (requested.type()) {
            // Twitch's video object has no game, so the client's game id is kept. It only picks
            // recommendation seeds, but it has to look like a Twitch game id.
            case VIDEO -> isGameId(requested.gameId())
                    ? twitchService.getVideo(requested.twitchId()).map(video -> new ItemEntity(requested.gameId(), video))
                    : Optional.empty();
            case CLIP -> twitchService.getClip(requested.twitchId()).map(ItemEntity::new);
            // Twitch looks streams up by channel, not by id: the stream live on the claimed
            // channel has to be the one the client named
            case STREAM -> channelLogin(requested.url())
                    .flatMap(twitchService::getLiveStream)
                    .filter(stream -> stream.id().equals(requested.twitchId()))
                    .map(ItemEntity::new);
        };
        return item.orElseThrow(UnknownItemException::new);
    }


    private static boolean isGameId(String gameId) {
        return gameId != null && GAME_ID.matcher(gameId).matches();
    }


    private static Optional<String> channelLogin(String url) {
        if (url == null) {
            return Optional.empty();
        }
        Matcher matcher = CHANNEL_URL.matcher(url);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }


    // Spring Data JDBC wraps the translated SQL exception thrown by save()
    private static boolean isDuplicateKey(DbActionExecutionException e) {
        return e.getCause() instanceof DuplicateKeyException;
    }


    @CacheEvict(cacheNames = "recommend_items", key = "#user")
    public void unsetFavoriteItem(UserEntity user, String twitchId) {
        ItemEntity item = itemRepository.findByTwitchId(twitchId);
        if (item != null) {
            favoriteRecordRepository.delete(user.id(), item.id());
        }
    }


    public List<ItemEntity> getFavoriteItems(UserEntity user) {
        List<Long> favoriteItemIds = favoriteRecordRepository.findFavoriteItemIdsByUserId(user.id());
        return itemRepository.findAllById(favoriteItemIds);
    }


    public TypeGroupedItemList getGroupedFavoriteItems(UserEntity user) {
        List<ItemEntity> items = getFavoriteItems(user);
        return new TypeGroupedItemList(items);
    }
}
