package pet.project.streamEra.videoDomain.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import pet.project.streamEra.configs.RabbitMqConfig;
import pet.project.streamEra.videoDomain.dtos.VideoDto;
import pet.project.streamEra.videoDomain.dtos.VideoUpdateDto;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.entity.VideoStatus;
import pet.project.streamEra.videoDomain.rabbitmq.VideoMessageSender;
import pet.project.streamEra.videoDomain.repository.CategoryRepository;
import pet.project.streamEra.videoDomain.repository.VideoRepository;

@Service
@AllArgsConstructor
public class VideoService {
    private final VideoRepository videoRepository;
    private final CategoryRepository categoryRepository;
    private final VideoMessageSender videoMessageSender;

    private final String URL = "raw/";
    private final String FILENAME = "source";

    public Video findById(Long id) {
        return videoRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    public Video findByName(String name) {
        return videoRepository.findByName(name).orElseThrow(EntityNotFoundException::new);
    }


    // checks video metadata,
    // then create key -> /raw/{videoId}/source.mp4
    // return video with 'PENDING' status
    public String addVideo(VideoDto videoDto) {
        Video newVideo = new Video();
        newVideo.setName(videoDto.name());
        newVideo.setSize(videoDto.size()); // initial size (verify after upload)
        newVideo.setVideoLength(0L);
        newVideo.setVideoStatus(VideoStatus.PENDING);

        newVideo = videoRepository.save(newVideo);
        String key = URL.concat(newVideo.getVideoId().toString())
                .concat(FILENAME)
                .concat(".")
                .concat("mp4");
        return URL + key;
    }

    // received processed video key, new key == new video url
    // changes original video url and status
    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void receiveMessage(String message) {
        System.out.println("Received <" + message + ">");
        String[] split = message.split("/"); // should be /hls/{videoId}/...
        Long id = Long.valueOf(split[1]);
        Video video = findById(id);
        videoRepository.save(video);
    }


//    // checks if processed video is avaliable
//    private void checkVideoAvailablity(Long videoId){
//
//        String url = String.join("/", "hls", videoId.toString(), "index.m3u8");
//
//    }


    public Video updateVideo(VideoUpdateDto videoUpdateDto) {
        Video videoToUpdate = videoRepository.findById(videoUpdateDto.videoId())
                .orElseThrow(EntityNotFoundException::new);
        if (videoUpdateDto.categoryList() != null) {
            videoToUpdate.getCategorySet().addAll(videoUpdateDto.categoryList());
        }
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

    // confirms that video was uploaded
    public String confirm(Long videoId) {
        String url = String.join("/", "raw", videoId.toString(), "source.mp4");


        return null;
    }
}

