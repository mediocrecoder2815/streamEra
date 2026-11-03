package pet.project.streamEra;


import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pet.project.streamEra.videoDomain.dtos.VideoDto;
import pet.project.streamEra.videoDomain.dtos.VideoUpdateDto;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.entity.VideoStatus;
import pet.project.streamEra.videoDomain.repository.CategoryRepository;
import pet.project.streamEra.videoDomain.repository.VideoRepository;
import pet.project.streamEra.videoDomain.service.VideoService;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class VideoServiceTest {
    @Mock
    private VideoRepository videoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private VideoService videoService;

    @BeforeEach
    void setUp(){
        videoService = new VideoService(videoRepository, categoryRepository);


    }

    @Test
    @DisplayName(value = "get: Return video if given correct id")
    void getVideo(){
        // ARRANGE
        Video exampleVideo = new Video();
        exampleVideo.setName("Test");
        exampleVideo.setVideoId(1L);
        when(videoRepository.findById(1L)).thenReturn(Optional.of(exampleVideo));
        // ACT
        Video videoOutput = videoService.findById(1L);
        // ASSERT
        assertThat(videoOutput).isSameAs(exampleVideo);
        assertThat(videoOutput.getName()).isEqualTo("Test");
    }

    @Test
    @DisplayName("get: Should throw exception for bad id")
    void throwExcWhenGivenBadId(){
        // ARRANGE
        when(videoRepository.findById(2L)).thenReturn(Optional.empty());
        // ACT + ASSERT
        assertThatThrownBy(() -> videoService.findById(2L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("add: Should create video")
    void shouldCreateVideo(){
        // ARRANGE
        Long videoSize = 1293945L;
        var inputVideo = new VideoDto("Test", "MP4", videoSize);

        // Save is called with a NEW Video created inside the service, so we cannot
        // match it by identity. Accept any Video and return the argument as-is
        // (this is what a real repository would do after persisting it).
        when(videoRepository.save(any(Video.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        Video createdVideo = videoService.addVideo(inputVideo);

        // ASSERT: inspect what was actually passed to save()
        ArgumentCaptor<Video> savedVideo = ArgumentCaptor.forClass(Video.class);
        verify(videoRepository).save(savedVideo.capture());

        assertThat(createdVideo).isSameAs(savedVideo.getValue());
        assertThat(createdVideo.getName()).isEqualTo("Test");
        assertThat(createdVideo.getVideoStatus()).isEqualTo(VideoStatus.PENDING);
    }

    @Test
    @DisplayName(value = "create: Should throw exc with empty name")
    void shouldThrowWhenGiveBadName(){
        // ARRANGE
        VideoDto videoInput = new VideoDto("","MP4",555L);
        // ACT + ASSERT
        assertThatThrownBy(() -> videoService.addVideo(videoInput)).
                            isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName(value = "update: Should update video")
    void shouldUpdateVideo(){
        // ARRANGE
        Video exampleVideo = new Video();
        exampleVideo.setName("Test");
        exampleVideo.setVideoId(1L);

        // NOTE: categoryList must NOT be null: the service calls
        // categorySet.addAll(categoryList), and addAll(null) throws an NPE.
        VideoUpdateDto updateDto = new VideoUpdateDto(
                1L, "new name", List.of(), "new link", "OK");

        when(videoRepository.findById(1L)).thenReturn(Optional.of(exampleVideo));
        // save returns whatever it receives, like a real repository does
        when(videoRepository.save(any(Video.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        Video updatedVideo = videoService.updateVideo(updateDto);

        // ASSERT: what the service PASSED IN to save().
        // This part is real behaviour: the service must mutate the entity it
        // loaded (not a copy) and hand that exact instance to the repository.
        ArgumentCaptor<Video> savedVideo = ArgumentCaptor.forClass(Video.class);
        verify(videoRepository).save(savedVideo.capture());

        Video passedToSave = savedVideo.getValue();
        assertThat(passedToSave).isSameAs(exampleVideo);
        assertThat(passedToSave.getName()).isEqualTo("new name");
        assertThat(passedToSave.getVideoUrl()).isEqualTo("new link");
        assertThat(passedToSave.getVideoStatus()).isEqualTo(VideoStatus.OK);

        // ASSERT: what the service RETURNED.
        // Assert values, not identity: whether save() returns the same instance
        // is a repository detail, not a VideoService guarantee.
        assertThat(updatedVideo.getName()).isEqualTo("new name");
        assertThat(updatedVideo.getVideoStatus()).isEqualTo(VideoStatus.OK);
    }

    @Test
    @DisplayName(value = "update: Should not update video that doesn't exists")
    void shouldThrowWithBadId(){
        // ARRANGE
        Video exampleVideo = new Video();
        VideoUpdateDto updateDto = new VideoUpdateDto(
                2L, "new name", List.of(), "new link", "OK");
        exampleVideo.setName("Test");
        exampleVideo.setVideoId(1L);
        when(videoRepository.findById(2L)).thenReturn(Optional.empty());
        // ACT + ASSERT
        assertThatThrownBy(() -> videoService.updateVideo(updateDto)).isInstanceOf(EntityNotFoundException.class);
    }
    @Test
    @DisplayName(value = "delete: Should delete with correct id")
    void ShouldDeleteVideoById(){
        // ARRANGE
        Video exampleVideo = new Video();
        exampleVideo.setName("Test");
        exampleVideo.setVideoId(1L);
        // ACT
        videoService.deleteVideo(1L);
        // ASSERT
        assertThatThrownBy(() -> videoService.findById(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
