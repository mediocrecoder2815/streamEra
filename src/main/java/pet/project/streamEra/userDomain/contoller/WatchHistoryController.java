package pet.project.streamEra.userDomain.contoller;

import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pet.project.streamEra.userDomain.dtos.WatchHistoryDto;
import pet.project.streamEra.userDomain.dtos.WatchHistoryProgressDto;
import pet.project.streamEra.userDomain.entity.WatchHistory;
import pet.project.streamEra.userDomain.service.WatchHistoryService;

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

    @MutationMapping(name ="update_watch_historyProgress")
    public WatchHistory updateProgress(@Argument(name = "updateInput") WatchHistoryProgressDto dto){
        return watchHistoryService.updateProgress(dto);
    }
}
