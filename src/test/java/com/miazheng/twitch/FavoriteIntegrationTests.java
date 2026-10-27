package com.miazheng.twitch;


import com.miazheng.twitch.db.entity.ItemEntity;
import com.miazheng.twitch.model.TypeGroupedItemList;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;


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


    @Test
    public void favorite_sameNewItemByManyUsersAtOnce_shouldSucceedForEveryone() throws Exception {
        Map<String, Object> body = favoriteVideoBody("video-" + UUID.randomUUID(), "1001");
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
        String twitchId = "video-" + UUID.randomUUID();
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
