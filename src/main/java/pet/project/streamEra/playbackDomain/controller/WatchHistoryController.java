package pet.project.streamEra.playbackDomain.controller;


import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pet.project.streamEra.playbackDomain.entity.WatchHistory;
import pet.project.streamEra.playbackDomain.entity.WatchHistoryDto;
import pet.project.streamEra.playbackDomain.service.WatchHistoryService;

import java.security.Principal;
import java.util.List;

@Controller
@AllArgsConstructor
public class WatchHistoryController {
    private WatchHistoryService watchHistoryService;

    @QueryMapping(name = "watchHistory")
    public List<WatchHistory> findByUserId(Principal principal) {
        return watchHistoryService.getMe(principal);
    }


    @QueryMapping(name = "watchHistoryByVideo")
    public WatchHistory watchHistoryByVideo(Principal principal, @Argument Long videoId) {
        return watchHistoryService.getWhByVideo(videoId, principal);
    }

    @MutationMapping(name = "update_watch_history")
    public WatchHistory updateWatchHistory(WatchHistoryDto watchHistoryDto){
        return watchHistoryService.updateWH(watchHistoryDto);
    }
}
