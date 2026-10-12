package pet.project.streamEra.videoDomain.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.repository.CategoryRepository;
import pet.project.streamEra.videoDomain.repository.VideoRepository;

import java.util.List;

@Service
@AllArgsConstructor
public class VideoService {
    private final VideoRepository videoRepository;
    private final CategoryRepository categoryRepository;

    public List<Video> findAllVideos() {
        return videoRepository.findAll();
    }

    public Video findById(Long id) {
        return videoRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    public Video findByName(String name) {
        return videoRepository.findByName(name).orElseThrow(EntityNotFoundException::new);
    }
}
