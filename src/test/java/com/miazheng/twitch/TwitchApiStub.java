package com.miazheng.twitch;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;


import java.util.List;
import java.util.Map;


import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.havingExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;


/**
 * Stands in for the Twitch token endpoint and the Helix API. Serves three top games, each with
 * one live stream ({@code s<game>}), two videos ({@code v<game>-1}, {@code v<game>-2}) and two
 * clips ({@code c<game>-1}, {@code c<game>-2}).
 */
public class TwitchApiStub {


    public static final List<String> TOP_GAME_IDS = List.of("1001", "1002", "1003");


    private static final ObjectMapper JSON = new ObjectMapper();


    private final WireMockServer server = new WireMockServer(options().dynamicPort());


    public static TwitchApiStub start() {
        TwitchApiStub stub = new TwitchApiStub();
        stub.server.start();
        stub.stubEndpoints();
        return stub;
    }


    public String baseUrl() {
        return server.baseUrl();
    }


    public void resetRequests() {
        server.resetRequests();
    }


    public int requestCount(String path) {
        return server.findAll(getRequestedFor(urlPathEqualTo(path))).size();
    }


    private void stubEndpoints() {
        server.stubFor(post("/oauth2/token").willReturn(okJson(json(Map.of(
                "access_token", "test-token",
                "expires_in", 3600,
                "token_type", "bearer"
        )))));
        server.stubFor(get(urlPathEqualTo("/helix/games/top"))
                .willReturn(okJson(data(TOP_GAME_IDS.stream().map(TwitchApiStub::game).toList()))));
        server.stubFor(get(urlPathEqualTo("/helix/streams"))
                .withQueryParam("game_id", havingExactly(TOP_GAME_IDS.toArray(String[]::new)))
                .willReturn(okJson(data(TOP_GAME_IDS.stream().map(TwitchApiStub::stream).toList()))));
        for (String gameId : TOP_GAME_IDS) {
            server.stubFor(get(urlPathEqualTo("/helix/games"))
                    .withQueryParam("name", equalTo("Game " + gameId))
                    .willReturn(okJson(data(List.of(game(gameId))))));
            server.stubFor(get(urlPathEqualTo("/helix/streams"))
                    .withQueryParam("game_id", havingExactly(gameId))
                    .willReturn(okJson(data(List.of(stream(gameId))))));
            server.stubFor(get(urlPathEqualTo("/helix/videos"))
                    .withQueryParam("game_id", equalTo(gameId))
                    .willReturn(okJson(data(List.of(video(gameId, 1), video(gameId, 2))))));
            server.stubFor(get(urlPathEqualTo("/helix/clips"))
                    .withQueryParam("game_id", equalTo(gameId))
                    .willReturn(okJson(data(List.of(clip(gameId, 1), clip(gameId, 2))))));
        }
    }


    private static Map<String, Object> game(String gameId) {
        return Map.of(
                "id", gameId,
                "name", "Game " + gameId,
                "box_art_url", "https://static-cdn.jtvnw.net/ttv-boxart/" + gameId + "-{width}x{height}.jpg"
        );
    }


    private static Map<String, Object> stream(String gameId) {
        return Map.of(
                "id", "s" + gameId,
                "user_name", "streamer" + gameId,
                "game_id", gameId,
                "title", "Live " + gameId,
                "viewer_count", 100,
                "thumbnail_url", "https://static-cdn.jtvnw.net/s" + gameId + "-{width}x{height}.jpg"
        );
    }


    private static Map<String, Object> video(String gameId, int n) {
        String id = "v" + gameId + "-" + n;
        return Map.of(
                "id", id,
                "user_name", "streamer" + gameId,
                "title", "Video " + id,
                "url", "https://www.twitch.tv/videos/" + id,
                "thumbnail_url", "https://static-cdn.jtvnw.net/" + id + "-%{width}x%{height}.jpg",
                "view_count", 50 * n,
                "duration", "1h2m3s"
        );
    }


    private static Map<String, Object> clip(String gameId, int n) {
        String id = "c" + gameId + "-" + n;
        return Map.of(
                "id", id,
                "broadcaster_name", "streamer" + gameId,
                "game_id", gameId,
                "title", "Clip " + id,
                "url", "https://clips.twitch.tv/" + id,
                "thumbnail_url", "https://clips-media-assets2.twitch.tv/" + id + ".jpg",
                "view_count", 20 * n,
                "duration", 30.0
        );
    }


    private static String data(List<Map<String, Object>> items) {
        return json(Map.of("data", items));
    }


    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
