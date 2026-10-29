package com.miazheng.twitch.favorite;


import com.miazheng.twitch.db.FavoriteRecordRepository;
import com.miazheng.twitch.db.ItemRepository;
import com.miazheng.twitch.db.entity.FavoriteRecordEntity;
import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.db.entity.UserEntity;
import com.miazheng.twitch.model.TypeGroupedItemList;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.stereotype.Service;


import java.time.Instant;
import java.util.List;


@Service
public class FavoriteService {


    private final ItemRepository itemRepository;
    private final FavoriteRecordRepository favoriteRecordRepository;


    public FavoriteService(ItemRepository itemRepository,
                           FavoriteRecordRepository favoriteRecordRepository) {
        this.itemRepository = itemRepository;
        this.favoriteRecordRepository = favoriteRecordRepository;
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


    private ItemEntity findOrSaveItem(ItemEntity item) {
        ItemEntity persistedItem = itemRepository.findByTwitchId(item.twitchId());
        if (persistedItem != null) {
            return persistedItem;
        }
        try {
            return itemRepository.save(item);
        } catch (DbActionExecutionException e) {
            if (isDuplicateKey(e)) {
                return itemRepository.findByTwitchId(item.twitchId());
            }
            throw e;
        }
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
