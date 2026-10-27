package com.miazheng.twitch.db;


import com.miazheng.twitch.db.entity.ItemEntity;
import org.springframework.data.repository.ListCrudRepository;


public interface ItemRepository extends ListCrudRepository<ItemEntity, Long> {

    // select + from items where twitch_id = :twitchid
    ItemEntity findByTwitchId(String twitchId);
}
