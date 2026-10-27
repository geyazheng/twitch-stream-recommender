package com.miazheng.twitch;


import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.model.TypeGroupedItemList;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;


import java.util.Map;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;


public class FavoriteIntegrationTests extends IntegrationTest {


    @Test
    public void favorite_shouldBeListedRejectedAsDuplicateAndRemovable() {
        String session = registerAndLogin();
        String twitchId = "video-" + UUID.randomUUID();
        Map<String, Object> body = favoriteVideoBody(twitchId, "1001");


        assertThat(favorite(session, body)).isEqualTo(HttpStatus.OK);
        TypeGroupedItemList favorites = getFavorites(session);
        assertThat(favorites.videos()).extracting(ItemEntity::twitchId).containsExactly(twitchId);
        assertThat(favorites.streams()).isEmpty();
        assertThat(favorites.clips()).isEmpty();


        assertThat(favorite(session, body)).isEqualTo(HttpStatus.BAD_REQUEST);


        assertThat(request(HttpMethod.DELETE, "/favorite", session, body, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getFavorites(session).videos()).isEmpty();
    }


    @Test
    public void favorite_withoutLogin_shouldBeUnauthorized() {
        assertThat(request(HttpMethod.GET, "/favorite", null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(favorite(null, favoriteVideoBody("video-" + UUID.randomUUID(), "1001"))).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
