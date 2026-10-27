package com.miazheng.twitch;


import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.model.TypeGroupedItemList;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;


import static org.assertj.core.api.Assertions.assertThat;


public class RecommendationIntegrationTests extends IntegrationTest {


    @Test
    public void recommendation_withoutLogin_shouldUseTopGames() {
        TypeGroupedItemList items = rest.getForObject("/recommendation", TypeGroupedItemList.class);


        assertThat(items.streams()).extracting(ItemEntity::twitchId).containsExactlyInAnyOrder("s1001", "s1002", "s1003");
        assertThat(items.videos()).extracting(ItemEntity::twitchId)
                .containsExactlyInAnyOrder("v1001-1", "v1001-2", "v1002-1", "v1002-2", "v1003-1", "v1003-2");
        assertThat(items.clips()).extracting(ItemEntity::twitchId)
                .containsExactlyInAnyOrder("c1001-1", "c1001-2", "c1002-1", "c1002-2", "c1003-1", "c1003-2");
    }


    @Test
    public void recommendation_withFavorites_shouldFollowFavoritedGamesAndSkipFavoritedItems() {
        String session = registerAndLogin();
        assertThat(favorite(session, favoriteVideoBody("v1002-1", "1002"))).isEqualTo(HttpStatus.OK);


        TypeGroupedItemList items = request(HttpMethod.GET, "/recommendation", session, null, TypeGroupedItemList.class).getBody();


        assertThat(items.streams()).extracting(ItemEntity::twitchId).containsExactly("s1002");
        assertThat(items.videos()).extracting(ItemEntity::twitchId).containsExactly("v1002-2");
        assertThat(items.clips()).extracting(ItemEntity::twitchId).containsExactlyInAnyOrder("c1002-1", "c1002-2");
    }
}
