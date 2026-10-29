package com.miazheng.twitch.external;


import com.miazheng.twitch.external.model.Clip;
import com.miazheng.twitch.external.model.Game;
import com.miazheng.twitch.external.model.Stream;
import com.miazheng.twitch.external.model.Video;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


import java.util.List;


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
}
