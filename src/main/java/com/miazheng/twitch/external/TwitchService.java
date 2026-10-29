package com.miazheng.twitch.external;


import com.miazheng.twitch.external.model.Clip;
import com.miazheng.twitch.external.model.Game;
import com.miazheng.twitch.external.model.Stream;
import com.miazheng.twitch.external.model.Video;
import org.springframework.cache.annotation.Cacheable;
import feign.FeignException;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Optional;


@Service
public class TwitchService {


    private final TwitchApiClient twitchApiClient;


    public TwitchService(TwitchApiClient twitchApiClient) {
        this.twitchApiClient = twitchApiClient;
    }


    @Cacheable("top_games")
    public List<Game> getTopGames() {
        return twitchApiClient.getTopGames().data();
    }


    @Cacheable("games_by_name")
    public List<Game> getGames(String name) {
        return twitchApiClient.getGames(name).data();
    }


    public List<Stream> getStreams(List<String> gameIds, int first) {
        return twitchApiClient.getStreams(gameIds, first).data();
    }


    // Twitch sorts by newest by default, which mostly returns VODs of streams that are still
    // live: no thumbnail yet and almost no views. This week's most viewed are worth showing.
    public List<Video> getVideos(String gameId, int first) {
        return twitchApiClient.getVideos(gameId, first, "views", "week").data();
    }


    public List<Clip> getClips(String gameId, int first) {
        return twitchApiClient.getClips(gameId, first).data();
    }


    // Single-item lookups. Twitch answers an unknown id with 404, a malformed one with 400, and
    // a channel that is not live with an empty list; all of them mean "no such item".
    public Optional<Video> getVideo(String id) {
        try {
            return first(twitchApiClient.getVideosById(id).data());
        } catch (FeignException.NotFound | FeignException.BadRequest e) {
            return Optional.empty();
        }
    }


    public Optional<Clip> getClip(String id) {
        try {
            return first(twitchApiClient.getClipsById(id).data());
        } catch (FeignException.NotFound | FeignException.BadRequest e) {
            return Optional.empty();
        }
    }


    public Optional<Stream> getLiveStream(String userLogin) {
        try {
            return first(twitchApiClient.getStreamsByUserLogin(userLogin).data());
        } catch (FeignException.NotFound | FeignException.BadRequest e) {
            return Optional.empty();
        }
    }


    private static <T> Optional<T> first(List<T> items) {
        return items == null || items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }
}
