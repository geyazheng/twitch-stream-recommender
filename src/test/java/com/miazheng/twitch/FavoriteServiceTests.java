package com.miazheng.twitch;


import com.miazheng.twitch.db.FavoriteRecordRepository;
import com.miazheng.twitch.db.ItemRepository;
import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.db.entity.UserEntity;
import com.miazheng.twitch.external.TwitchService;
import com.miazheng.twitch.external.model.Video;
import com.miazheng.twitch.favorite.DuplicateFavoriteException;
import com.miazheng.twitch.favorite.FavoriteService;
import com.miazheng.twitch.favorite.UnknownItemException;
import com.miazheng.twitch.model.ItemType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;


import java.util.Optional;


@ExtendWith(MockitoExtension.class)
public class FavoriteServiceTests {


    @Mock private ItemRepository itemRepository;
    @Mock private FavoriteRecordRepository favoriteRecordRepository;
    @Mock private TwitchService twitchService;


    private FavoriteService favoriteService;


    @BeforeEach
    public void setup() {
        favoriteService = new FavoriteService(itemRepository, favoriteRecordRepository, twitchService);
    }


    @Test
    public void whenItemNotExist_setFavoriteItem_shouldSaveItemAsTwitchDescribesIt() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity requested = new ItemEntity(null, "twitchId", "forged title", "javascript:alert(1)", "thumb", "broadcaster", "123", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(1L, "twitchId", "Twitch title", "https://www.twitch.tv/videos/twitchId", "twitch-thumb", "streamer", "123", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(null);
        Mockito.when(twitchService.getVideo("twitchId")).thenReturn(Optional.of(video("twitchId", "Twitch title")));
        Mockito.when(itemRepository.save(Mockito.any(ItemEntity.class))).thenReturn(persisted);


        favoriteService.setFavoriteItem(user, requested);


        Mockito.verify(itemRepository).save(Mockito.argThat(item -> item.title().equals("Twitch title")
                && item.url().equals("https://www.twitch.tv/videos/twitchId")
                && item.gameId().equals("123")));
    }


    @Test
    public void whenItemExist_setFavoriteItem_shouldNotSaveItem() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity item = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "123", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(1L, "twitchId", "title", "url", "thumb", "broadcaster", "123", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(persisted);


        favoriteService.setFavoriteItem(user, item);


        Mockito.verify(itemRepository, Mockito.never()).save(item);
        Mockito.verifyNoInteractions(twitchService);
    }


    @Test
    public void whenItemNotOnTwitch_setFavoriteItem_shouldThrowUnknownItem() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity requested = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "123", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(null);
        Mockito.when(twitchService.getVideo("twitchId")).thenReturn(Optional.empty());


        Assertions.assertThrows(UnknownItemException.class, () -> favoriteService.setFavoriteItem(user, requested));
        Mockito.verify(itemRepository, Mockito.never()).save(Mockito.any());
    }


    @Test
    public void whenVideoGameIdIsNotAnId_setFavoriteItem_shouldThrowUnknownItem() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity requested = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(null);


        Assertions.assertThrows(UnknownItemException.class, () -> favoriteService.setFavoriteItem(user, requested));
        Mockito.verifyNoInteractions(twitchService);
    }


    @Test
    public void whenItemNotExist_unsetFavoriteItem_shouldNotDeleteFavoriteRecord() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(null);


        favoriteService.unsetFavoriteItem(user, "twitchId");


        Mockito.verifyNoInteractions(favoriteRecordRepository);
    }


    @Test
    public void whenItemExist_unsetFavoriteItem_shouldDeleteFavoriteRecord() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity persisted = new ItemEntity(2L, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(persisted);


        favoriteService.unsetFavoriteItem(user, "twitchId");


        Mockito.verify(favoriteRecordRepository).delete(1L, 2L);
    }


    @Test
    public void whenFavoriteRecordExists_setFavoriteItem_shouldThrowDuplicateFavorite() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity item = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "123", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(1L, "twitchId", "title", "url", "thumb", "broadcaster", "123", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(persisted);
        Mockito.when(favoriteRecordRepository.save(Mockito.any())).thenThrow(duplicateKey());


        Assertions.assertThrows(DuplicateFavoriteException.class, () -> favoriteService.setFavoriteItem(user, item));
    }


    @Test
    public void whenItemSavedConcurrently_setFavoriteItem_shouldUseTheSavedItem() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity item = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "123", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(2L, "twitchId", "title", "url", "thumb", "broadcaster", "123", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(null, persisted);
        Mockito.when(twitchService.getVideo("twitchId")).thenReturn(Optional.of(video("twitchId", "title")));
        Mockito.when(itemRepository.save(Mockito.any(ItemEntity.class))).thenThrow(duplicateKey());


        favoriteService.setFavoriteItem(user, item);


        Mockito.verify(favoriteRecordRepository).save(Mockito.argThat(record -> record.userId() == 1L && record.itemId() == 2L));
    }


    private static DbActionExecutionException duplicateKey() {
        return new DbActionExecutionException(null, new DuplicateKeyException("Duplicate entry"));
    }


    private static Video video(String id, String title) {
        return new Video(id, null, null, null, "streamer", title, null, null, null,
                "https://www.twitch.tv/videos/" + id, "twitch-thumb", null, null, null, null, null, null);
    }
}
