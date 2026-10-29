package com.miazheng.twitch;


import com.miazheng.twitch.model.TypeGroupedItemList;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


import java.util.Map;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;


/**
 * Runs the whole application over HTTP against a MySQL container and a stubbed Twitch API.
 * All subclasses share one application context, so they share one database: give users and
 * items unique ids instead of relying on a clean slate.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"TWITCH_CLIENT_ID=test-client-id", "TWITCH_CLIENT_SECRET=test-client-secret"}
)
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {


    protected static final TwitchApiStub twitch = TwitchApiStub.start();


    @DynamicPropertySource
    static void twitchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.twitch-api.url", () -> twitch.baseUrl() + "/helix");
        registry.add("spring.security.oauth2.client.provider.twitch.token-uri", () -> twitch.baseUrl() + "/oauth2/token");
    }


    @Autowired
    protected TestRestTemplate rest;


    @Autowired
    private CacheManager cacheManager;


    @BeforeEach
    void resetCachesAndTwitchRequests() {
        for (String name : cacheManager.getCacheNames()) {
            cacheManager.getCache(name).clear();
        }
        twitch.resetRequests();
    }


    protected static String newUsername() {
        return "user-" + UUID.randomUUID();
    }


    protected HttpStatusCode register(String username, String password) {
        return rest.postForEntity("/register", Map.of(
                "username", username,
                "password", password,
                "first_name", "Test",
                "last_name", "User"
        ), String.class).getStatusCode();
    }


    /** Registers a new user, logs in, and returns the session cookie to send with later requests. */
    protected String registerAndLogin() {
        String username = newUsername();
        assertThat(register(username, "pass1234")).isEqualTo(HttpStatus.OK);


        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", "pass1234");
        ResponseEntity<String> login = rest.postForEntity("/login", form, String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    }


    protected <T> ResponseEntity<T> request(HttpMethod method, String path, String session, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (session != null) {
            headers.add(HttpHeaders.COOKIE, session);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), responseType);
    }


    protected HttpStatusCode favorite(String session, Map<String, Object> body) {
        return request(HttpMethod.POST, "/favorite", session, body, String.class).getStatusCode();
    }


    protected TypeGroupedItemList getFavorites(String session) {
        ResponseEntity<TypeGroupedItemList> response = request(HttpMethod.GET, "/favorite", session, null, TypeGroupedItemList.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }


    /** The request body the frontend sends to favorite a video. */
    protected static Map<String, Object> favoriteVideoBody(String twitchId, String gameId) {
        return Map.of("favorite", Map.of(
                "twitch_id", twitchId,
                "title", "Video " + twitchId,
                "url", "https://www.twitch.tv/videos/" + twitchId,
                "thumbnail_url", "https://static-cdn.jtvnw.net/" + twitchId + ".jpg",
                "broadcaster_name", "streamer" + gameId,
                "game_id", gameId,
                "item_type", "VIDEO"
        ));
    }
}
