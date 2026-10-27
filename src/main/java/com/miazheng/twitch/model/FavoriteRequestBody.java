package com.miazheng.twitch.model;


import com.miazheng.twitch.db.entity.ItemEntity;


public record FavoriteRequestBody(
        ItemEntity favorite
) {}
