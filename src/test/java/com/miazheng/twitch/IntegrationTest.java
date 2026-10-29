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


import java.util.ArrayList;
import java.util.List;
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


    private volatile String csrfToken;


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
        Map<String, String> body = Map.of(
                "username", username,
                "password", password,
                "first_name", "Test",
                "last_name", "User"
        );
        return request(HttpMethod.POST, "/register", null, body, String.class).getStatusCode();
    }


    protected ResponseEntity<String> login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        return rest.exchange("/login", HttpMethod.POST, new HttpEntity<>(form, browserHeaders(HttpMethod.POST, null)), String.class);
    }


    /** Registers a new user, logs in, and returns the session cookie to send with later requests. */
    protected String registerAndLogin() {
        String username = newUsername();
        assertThat(register(username, "pass1234")).isEqualTo(HttpStatus.OK);


        ResponseEntity<String> login = login(username, "pass1234");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return "JSESSIONID=" + cookieValue(login, "JSESSIONID");
    }


    protected <T> ResponseEntity<T> request(HttpMethod method, String path, String session, Object body, Class<T> responseType) {
        HttpHeaders headers = browserHeaders(method, session);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), responseType);
    }


    // What the frontend sends: the session cookie if logged in, and on anything but GET the
    // CSRF token twice, as the XSRF-TOKEN cookie and the X-XSRF-TOKEN header
    private HttpHeaders browserHeaders(HttpMethod method, String session) {
        HttpHeaders headers = new HttpHeaders();
        List<String> cookies = new ArrayList<>();
        if (session != null) {
            cookies.add(session);
        }
        if (method != HttpMethod.GET) {
            cookies.add("XSRF-TOKEN=" + csrfToken());
            headers.add("X-XSRF-TOKEN", csrfToken());
        }
        if (!cookies.isEmpty()) {
            headers.add(HttpHeaders.COOKIE, String.join("; ", cookies));
        }
        return headers;
    }


    // The token the server hands out with any response; one serves a whole test
    private String csrfToken() {
        if (csrfToken == null) {
            csrfToken = cookieValue(rest.getForEntity("/", String.class), "XSRF-TOKEN");
        }
        return csrfToken;
    }


    private static String cookieValue(ResponseEntity<?> response, String name) {
        List<String> setCookies = response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of());
        for (String setCookie : setCookies) {
            if (setCookie.startsWith(name + "=")) {
                return setCookie.substring(name.length() + 1).split(";", 2)[0];
            }
        }
        throw new AssertionError("No " + name + " cookie in " + setCookies);
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
