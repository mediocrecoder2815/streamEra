package pet.project.streamEra.playbackDomain;

import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pet.project.streamEra.videoDomain.entity.Category;
import pet.project.streamEra.videoDomain.entity.Video;

import java.util.List;
import java.util.Set;

@Controller
@AllArgsConstructor
public class PlaybackController {
    private final PlaybackService playbackService;

    @QueryMapping(name = "video_byId")
    public Video getVideoById(@Argument Long videoId){
        return playbackService.getVideoById(videoId);
    }

    @QueryMapping(name = "video_byName")
    public Video getVideoByName(@Argument String name){
        return playbackService.getVideoByName(name);
    }
    @QueryMapping(name = "videos")
    public List<Video> getAllVideos(){
        return playbackService.getAllVideos();
    }

    @QueryMapping(name = "categories")
    public List<Category> getAllCategories(){
        return playbackService.getAllCategories();
    }
}
