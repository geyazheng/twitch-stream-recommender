package com.miazheng.twitch;


import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.external.model.Game;
import com.miazheng.twitch.model.ItemType;
import com.miazheng.twitch.model.TypeGroupedItemList;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;


public class SearchIntegrationTests extends IntegrationTest {


    @Test
    public void getGame_withoutName_shouldReturnTopGames() {
        Game[] games = rest.getForObject("/game", Game[].class);


        assertThat(games).extracting(Game::name).containsExactly("Game 1001", "Game 1002", "Game 1003");
    }


    @Test
    public void getGame_withName_shouldSearchByName() {
        Game[] games = rest.getForObject("/game?game_name={name}", Game[].class, "Game 1002");


        assertThat(games).extracting(Game::id).containsExactly("1002");
    }


    @Test
    public void search_shouldGroupGameContentByType() {
        TypeGroupedItemList items = rest.getForObject("/search?game_id=1001", TypeGroupedItemList.class);


        assertThat(items.streams()).extracting(ItemEntity::twitchId).containsExactly("s1001");
        assertThat(items.streams()).extracting(ItemEntity::url).containsExactly("https://www.twitch.tv/streamer1001");
        assertThat(items.videos()).extracting(ItemEntity::twitchId).containsExactly("v1001-1", "v1001-2");
        assertThat(items.clips()).extracting(ItemEntity::twitchId).containsExactly("c1001-1", "c1001-2");
        assertThat(items.videos()).allSatisfy(video -> {
            assertThat(video.type()).isEqualTo(ItemType.VIDEO);
            assertThat(video.gameId()).isEqualTo("1001");
        });
    }
}
