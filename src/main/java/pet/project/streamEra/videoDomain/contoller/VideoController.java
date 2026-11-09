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

    @MutationMapping(name ="add_video")
    public String addVideo(@Argument VideoDto videoDto){
        return videoService.addVideo(videoDto);
    }

    @MutationMapping(name = "update_video")
    public Video updateVideoName(@Argument VideoUpdateDto videoUpdateDto){
        return videoService.updateVideo(videoUpdateDto);
    }

    @MutationMapping(name ="delete_video")
    public String deleteVideo(@Argument Long videoId){
        return videoService.deleteVideo(videoId);
    }

    @MutationMapping(name = "confirm")
    public String confirm(@Argument Long videoId){
        return videoService.confirm(videoId);
    }
}
