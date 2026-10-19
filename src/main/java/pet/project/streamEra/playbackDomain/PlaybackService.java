package pet.project.streamEra.playbackDomain;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.entity.VideoStatus;
import pet.project.streamEra.videoDomain.repository.VideoRepository;

import java.util.List;

@Service
@AllArgsConstructor
public class PlaybackService {
    private final VideoRepository videoRepository;

    public Video getVideoById(Long id){
        Video video = videoRepository.findById(id).
                orElseThrow(EntityNotFoundException::new);

        if(isValidVideo(video)){
            return null;
        }
        return video;
    }
    public Video getVideoByName(String name){
        Video video = videoRepository.findByName(name).
                orElseThrow(EntityNotFoundException::new);
        if (isValidVideo(video)){
            return null;
        }
        return video;
    }


    private boolean isValidVideo(Video video){
        return !video.getVideoStatus().equals(VideoStatus.OK);
    }

    public List<Video> getAllVideos() {
        List<Video> videos = videoRepository.findAll();

        return videos.stream()
                .filter(v -> v.getVideoStatus().equals(VideoStatus.OK)
                        || v.getVideoStatus().equals(VideoStatus.PENDING)).toList();
    }
}
