package pet.project.streamEra.videoDomain.contoller;


import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pet.project.streamEra.videoDomain.dtos.VideoDto;
import pet.project.streamEra.videoDomain.dtos.VideoUpdateDto;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.service.VideoService;

import java.util.List;

@Controller
@AllArgsConstructor
public class VideoController {
    private final VideoService videoService;

    @QueryMapping(name = "videos")
    public List<Video> videos() {
        return videoService.findAllVideos();
    }

    @QueryMapping(name = "video")
    public Video video(@Argument Long videoId) {
        return videoService.findById(videoId);
    }

    @QueryMapping(name = "videoByName")
    public Video  videoByName(@Argument String name) {
        return videoService.findByName(name);
    }
    @MutationMapping(name ="add_video")
    public Video addVideo(VideoDto videoDto){
        return videoService.addVideo(videoDto);
    }
    @MutationMapping(name = "update_video")
    public Video updateVideoName(VideoUpdateDto videoUpdateDto){
        return videoService.updateVideo(videoUpdateDto);
    }
}
