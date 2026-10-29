package com.miazheng.twitch.external;


import com.miazheng.twitch.external.model.ClipResponse;
import com.miazheng.twitch.external.model.GameResponse;
import com.miazheng.twitch.external.model.StreamResponse;
import com.miazheng.twitch.external.model.VideoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


import java.util.List;


@FeignClient(name = "twitch-api")
public interface TwitchApiClient {


    @GetMapping("/games")
    GameResponse getGames(@RequestParam("name") String name);


    @GetMapping("/games/top")
    GameResponse getTopGames();


    @GetMapping("/videos")
    VideoResponse getVideos(
            @RequestParam("game_id") String gameId,
            @RequestParam("first") int first,
            @RequestParam("sort") String sort,
            @RequestParam("period") String period
    );


    @GetMapping("/videos")
    VideoResponse getVideosById(@RequestParam("id") String id);


    @GetMapping("/clips")
    ClipResponse getClips(@RequestParam("game_id") String gameId, @RequestParam("first") int first);


    @GetMapping("/clips")
    ClipResponse getClipsById(@RequestParam("id") String id);


    @GetMapping("/streams")
    StreamResponse getStreamsByUserLogin(@RequestParam("user_login") String userLogin);


    @GetMapping("/streams")
    StreamResponse getStreams(@RequestParam("game_id") List<String> gameIds, @RequestParam("first") int first);
}
