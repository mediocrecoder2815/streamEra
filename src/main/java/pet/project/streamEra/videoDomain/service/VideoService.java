package pet.project.streamEra.videoDomain.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pet.project.streamEra.configs.AwsProperties;
import pet.project.streamEra.configs.RabbitMqConfig;
import pet.project.streamEra.videoDomain.dtos.UploadTicket;
import pet.project.streamEra.videoDomain.dtos.VideoDto;
import pet.project.streamEra.videoDomain.dtos.VideoUpdateDto;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.entity.VideoEvent;
import pet.project.streamEra.videoDomain.entity.VideoStatus;
import pet.project.streamEra.videoDomain.rabbitmq.VideoMessageSender;
import pet.project.streamEra.videoDomain.repository.CategoryRepository;
import pet.project.streamEra.videoDomain.repository.VideoRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@AllArgsConstructor
@Slf4j
public class VideoService {
    private final VideoRepository videoRepository;
    private final CategoryRepository categoryRepository;
    private final VideoMessageSender videoMessageSender;
//    private final Deque<Long> badVideos = new ArrayDeque<>();
    private final AwsProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    private final String prefix = "raw";
    // default file name
    private final String FILENAME = "source";
    // ~2 gb
    private final long sizeLimit = 2_000_000_000L;
    private static final String CONTENT_TYPE = "video/mp4";
    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

    public Video findById(Long id) {
        return videoRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    public Video findByName(String name) {
        return videoRepository.findByName(name).orElseThrow(EntityNotFoundException::new);
    }


    // checks video metadata,
    // then create key -> /raw/{videoId}/source.mp4
    // return video with 'PENDING' status
    public UploadTicket addVideo(VideoDto videoDto) {
        log.info("Initializing video with name: {}", videoDto.name());
        Video newVideo = new Video();
        newVideo.setName(videoDto.name());
        newVideo.setSize(videoDto.size()); // initial size (verify after upload)
        newVideo.setVideoLength(0L);
        newVideo.setVideoStatus(VideoStatus.PENDING);
        newVideo.setVideoUrl("");
        newVideo = videoRepository.save(newVideo);

        String key = String.join("/", prefix, newVideo.getVideoId().toString(), FILENAME + ".mp4");
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .contentType(CONTENT_TYPE)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putObjectRequest)
                .build();
        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        log.info("Created video with id: {} and key: {}", newVideo.getVideoId(), key);
        return new UploadTicket(
                newVideo.getVideoId(),
                key,
                uploadUrl,
                CONTENT_TYPE,
                Math.toIntExact(UPLOAD_URL_TTL.toSeconds())
        );
    }

    // received processed video key, new key == new video url
    // changes original video url and status
    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void receiveMessage(VideoEvent videoEvent) {
        switch (videoEvent.type()){
            case "processed":{
                Video videoToUpload = findById(videoEvent.videoId());
                videoToUpload.setVideoStatus(VideoStatus.OK);
                videoToUpload.setVideoUrl(publicUrl(videoEvent.message()));
                videoRepository.save(videoToUpload);
                break;
            }
            case "failed":{
                dumpVideo(videoEvent.videoId(), "Video with id " + videoEvent.videoId() + " failed");
                break;
            }
            case "upload":{
                // The worker owns upload events. This queue also receives them
                // because of the legacy event.video.* binding, so acknowledge
                // and ignore them here instead of failing the message.
                log.debug("Ignoring upload event for video {}", videoEvent.videoId());
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Received unrecognized video type");
            }
        }
    }

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
        try{
            HeadObjectResponse response = s3Client.headObject(b->
                        b.bucket(properties.s3().bucket()).key(url).build());
            long size = response.contentLength();
            String type = response.contentType();
            String etag = response.eTag();

            if(!CONTENT_TYPE.equals(type) || size > sizeLimit || etag == null){
                dumpVideo(videoId, "Video is not valid format or not uploaded yet");
                return "ERROR";
            }

            videoMessageSender.sendMessage(RabbitMqConfig.ROUTING_KEY_UPLOAD, new VideoEvent(videoId, url, "upload"));
            return "OK";
        }
        catch (S3Exception e) {
            if (e.statusCode() == 404) {
                Video toDelete = findById(videoId);
                toDelete.setVideoStatus(VideoStatus.ERROR);
                videoRepository.save(toDelete);
            }
            throw e;
        }
    }


    private void dumpVideo(Long videoId,  String message){
        Video wrongVideo = findById(videoId);
        wrongVideo.setVideoStatus(VideoStatus.ERROR);
        videoRepository.save(wrongVideo);
        log.error(message);
    }

    /**
     * Turns an object key into a browser-accessible URL. MinIO needs the
     * endpoint + bucket path; real AWS supports virtual-hosted-style URLs.
     */
    private String publicUrl(String key) {
        String cleanKey = key.replaceFirst("^/+", "");
        String endpoint = properties.endpoint();
        if (StringUtils.hasText(endpoint)) {
            return endpoint.replaceAll("/+$", "") + "/" + properties.s3().bucket() + "/" + cleanKey;
        }
        return "https://" + properties.s3().bucket() + ".s3." + properties.region() + ".amazonaws.com/" + cleanKey;
    }
}

