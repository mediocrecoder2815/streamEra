package pet.project.streamEra.videoDomain.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import pet.project.streamEra.videoDomain.dtos.VideoDto;
import pet.project.streamEra.videoDomain.dtos.VideoUpdateDto;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.entity.VideoStatus;
import pet.project.streamEra.videoDomain.repository.CategoryRepository;
import pet.project.streamEra.videoDomain.repository.VideoRepository;

import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class VideoService {
    private final VideoRepository videoRepository;
    private final CategoryRepository categoryRepository;
    private final Set<VideoStatus> statusSet = Set.of(VideoStatus.DELETED, VideoStatus.OK,
                                        VideoStatus.ERROR, VideoStatus.PENDING);
    public List<Video> findAllVideos() {
        return videoRepository.findAll();
    }

    public Video findById(Long id) {
        return videoRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    public Video findByName(String name) {
        return videoRepository.findByName(name).orElseThrow(EntityNotFoundException::new);
    }

    public Video addVideo(VideoDto videoDto) {
        Video newVideo = new Video();
        newVideo.setName(videoDto.name());
        // should be verified (format, size, etc...)
        newVideo.setVideoStatus(VideoStatus.PENDING);

        return videoRepository.save(newVideo);
    }

    public Video updateVideo(VideoUpdateDto videoUpdateDto) {
        Video videoToUpdate = videoRepository.findById(videoUpdateDto.videoId())
                .orElseThrow(EntityNotFoundException::new);
        videoToUpdate.getCategorySet().addAll(videoUpdateDto.categoryList());
        videoToUpdate.setName(videoUpdateDto.name());
        videoToUpdate.setVideoUrl(videoUpdateDto.videoUrl());
        videoToUpdate.setVideoStatus(VideoStatus.valueOf(videoUpdateDto.videoStatus()));

        return videoRepository.save(videoToUpdate);
    }

    public String deleteVideo(Long videoId) {
        Video videoToDelete = videoRepository.findById(videoId)
                .orElse(null);
        if(videoToDelete == null ){
            return "NOT FOUND";
        }
        videoToDelete.setVideoStatus(VideoStatus.DELETED);
        // here message should be added to query to delete video
        videoRepository.save(videoToDelete);
        return "OK";
    }
}

