package com.miazheng.twitch;


import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.model.TypeGroupedItemList;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


import static org.assertj.core.api.Assertions.assertThat;


public class FavoriteIntegrationTests extends IntegrationTest {


    private static final int CONCURRENT_REQUESTS = 8;


    @Test
    public void favorite_shouldBeListedRejectedAsDuplicateAndRemovable() {
        String session = registerAndLogin();
        String twitchId = twitch.addVideo("1001");
        Map<String, Object> body = favoriteVideoBody(twitchId, "1001");


        assertThat(favorite(session, body)).isEqualTo(HttpStatus.OK);
        TypeGroupedItemList favorites = getFavorites(session);
        assertThat(favorites.videos()).extracting(ItemEntity::twitchId).containsExactly(twitchId);
        assertThat(favorites.streams()).isEmpty();
        assertThat(favorites.clips()).isEmpty();


        ResponseEntity<String> duplicate = request(HttpMethod.POST, "/favorite", session, body, String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicate.getBody()).contains("duplicate_favorite").doesNotContain("com.miazheng");


        assertThat(request(HttpMethod.DELETE, "/favorite", session, body, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getFavorites(session).videos()).isEmpty();
    }


    @Test
    public void favorite_withoutLogin_shouldBeUnauthorized() {
        assertThat(request(HttpMethod.GET, "/favorite", null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(favorite(null, favoriteVideoBody("video-" + UUID.randomUUID(), "1001"))).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    // Items are shared between users, so whatever gets stored is what everyone who favorites the
    // same item sees. Only the id, type and game may come from the client.
    @Test
    public void favorite_withForgedMetadata_shouldStoreWhatTwitchSays() {
        String session = registerAndLogin();
        String videoId = twitch.addVideo("1002");
        Map<String, Object> forgedVideo = Map.of("favorite", Map.of(
                "twitch_id", videoId,
                "title", "FREE SKINS, log in here",
                "url", "javascript:alert(document.cookie)",
                "thumbnail_url", "https://attacker.example/pixel.png",
                "broadcaster_name", "Twitch Support",
                "game_id", "1002",
                "item_type", "VIDEO"
        ));
        // Only this test favorites c1003-1, so the item is not already stored
        Map<String, Object> forgedClip = Map.of("favorite", Map.of(
                "twitch_id", "c1003-1",
                "title", "FREE SKINS, log in here",
                "url", "https://attacker.example",
                "item_type", "CLIP"
        ));


        assertThat(favorite(session, forgedVideo)).isEqualTo(HttpStatus.OK);
        assertThat(favorite(session, forgedClip)).isEqualTo(HttpStatus.OK);


        TypeGroupedItemList favorites = getFavorites(session);
        assertThat(favorites.videos()).singleElement().satisfies(video -> {
            assertThat(video.title()).isEqualTo("Video " + videoId);
            assertThat(video.url()).isEqualTo("https://www.twitch.tv/videos/" + videoId);
            assertThat(video.thumbnailUrl()).startsWith("https://static-cdn.jtvnw.net/");
            assertThat(video.broadcasterName()).isEqualTo("streamer1002");
            assertThat(video.gameId()).isEqualTo("1002");
        });
        assertThat(favorites.clips()).singleElement().satisfies(clip -> {
            assertThat(clip.title()).isEqualTo("Clip c1003-1");
            assertThat(clip.url()).isEqualTo("https://clips.twitch.tv/c1003-1");
            assertThat(clip.gameId()).isEqualTo("1003");
        });
    }


    @Test
    public void favorite_ofItemNotOnTwitch_shouldBeRejected() {
        String session = registerAndLogin();


        ResponseEntity<String> response = request(HttpMethod.POST, "/favorite", session,
                favoriteVideoBody("v1001-made-up", "1001"), String.class);


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("unknown_item");
        assertThat(getFavorites(session).videos()).isEmpty();
    }


    // Twitch cannot look a stream up by id, only by channel, so the claimed channel is checked
    // against the stream that is actually live there. Only this test favorites s1003.
    @Test
    public void favorite_ofStream_shouldMatchTheChannelsLiveStream() {
        String session = registerAndLogin();


        assertThat(favorite(session, favoriteStreamBody("s1003", "https://www.twitch.tv/streamer1002"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(favorite(session, favoriteStreamBody("s1003", "https://attacker.example/streamer1003"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(favorite(session, favoriteStreamBody("s1003", "https://www.twitch.tv/streamer1003"))).isEqualTo(HttpStatus.OK);


        assertThat(getFavorites(session).streams()).singleElement().satisfies(stream -> {
            assertThat(stream.title()).isEqualTo("Live 1003");
            assertThat(stream.url()).isEqualTo("https://www.twitch.tv/streamer1003");
        });
    }


    @Test
    public void favorite_sameNewItemByManyUsersAtOnce_shouldSucceedForEveryone() throws Exception {
        Map<String, Object> body = favoriteVideoBody(twitch.addVideo("1001"), "1001");
        List<String> sessions = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            sessions.add(registerAndLogin());
        }


        List<Callable<HttpStatusCode>> requests = new ArrayList<>();
        for (String session : sessions) {
            requests.add(() -> favorite(session, body));
        }
        List<HttpStatusCode> statuses = runConcurrently(requests);


        assertThat(statuses).containsOnly(HttpStatus.OK);
    }


    @Test
    public void favorite_sameItemBySameUserAtOnce_shouldStoreOneAndRejectTheRest() throws Exception {
        String twitchId = twitch.addVideo("1001");
        Map<String, Object> body = favoriteVideoBody(twitchId, "1001");
        // Another user saves the item first, so the requests below only race on the favorite record
        assertThat(favorite(registerAndLogin(), body)).isEqualTo(HttpStatus.OK);
        String session = registerAndLogin();


        List<Callable<HttpStatusCode>> requests = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            requests.add(() -> favorite(session, body));
        }
        List<HttpStatusCode> statuses = runConcurrently(requests);


        assertThat(statuses).containsOnly(HttpStatus.OK, HttpStatus.BAD_REQUEST);
        assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(1);
        assertThat(getFavorites(session).videos()).extracting(ItemEntity::twitchId).containsExactly(twitchId);
    }


    private static Map<String, Object> favoriteStreamBody(String twitchId, String url) {
        return Map.of("favorite", Map.of("twitch_id", twitchId, "url", url, "title", "Live", "item_type", "STREAM"));
    }


    /** Starts all tasks at the same moment so their requests overlap, and returns their results in order. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
