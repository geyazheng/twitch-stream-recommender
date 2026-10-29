package com.miazheng.twitch;


import com.miazheng.twitch.db.FavoriteRecordRepository;
import com.miazheng.twitch.db.ItemRepository;
import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.db.entity.UserEntity;
import com.miazheng.twitch.favorite.DuplicateFavoriteException;
import com.miazheng.twitch.favorite.FavoriteService;
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


@ExtendWith(MockitoExtension.class)
public class FavoriteServiceTests {


    @Mock private ItemRepository itemRepository;
    @Mock private FavoriteRecordRepository favoriteRecordRepository;


    private FavoriteService favoriteService;


    @BeforeEach
    public void setup() {
        favoriteService = new FavoriteService(itemRepository, favoriteRecordRepository);
    }


    @Test
    public void whenItemNotExist_setFavoriteItem_shouldSaveItem() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity item = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(1L, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(null);
        Mockito.when(itemRepository.save(item)).thenReturn(persisted);


        favoriteService.setFavoriteItem(user, item);


        Mockito.verify(itemRepository).save(item);
    }


    @Test
    public void whenItemExist_setFavoriteItem_shouldNotSaveItem() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity item = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(1L, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(persisted);


        favoriteService.setFavoriteItem(user, item);


        Mockito.verify(itemRepository, Mockito.never()).save(item);
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
        ItemEntity item = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(1L, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(persisted);
        Mockito.when(favoriteRecordRepository.save(Mockito.any())).thenThrow(duplicateKey());


        Assertions.assertThrows(DuplicateFavoriteException.class, () -> favoriteService.setFavoriteItem(user, item));
    }


    @Test
    public void whenItemSavedConcurrently_setFavoriteItem_shouldUseTheSavedItem() {
        UserEntity user = new UserEntity(1L, "user", "foo", "bar", "123456");
        ItemEntity item = new ItemEntity(null, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        ItemEntity persisted = new ItemEntity(2L, "twitchId", "title", "url", "thumb", "broadcaster", "gameid", ItemType.VIDEO);
        Mockito.when(itemRepository.findByTwitchId("twitchId")).thenReturn(null, persisted);
        Mockito.when(itemRepository.save(item)).thenThrow(duplicateKey());


        favoriteService.setFavoriteItem(user, item);


        Mockito.verify(favoriteRecordRepository).save(Mockito.argThat(record -> record.userId() == 1L && record.itemId() == 2L));
    }


    private static DbActionExecutionException duplicateKey() {
        return new DbActionExecutionException(null, new DuplicateKeyException("Duplicate entry"));
    }
}
