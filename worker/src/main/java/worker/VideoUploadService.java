package worker;



/*
    Worker service responsible for proccessing/uploading video from client
    Once received message from videoService:
        - downloads video from S3 bucker slices it into chunks, send back to S3
        - sends message once processed
        - message ex. hls/{videoID}/index.m3u8
*/

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import worker.configs.AwsProperties;
import worker.configs.RabbitMqConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoUploadService {
    private final S3Client s3Client;
    private final AwsProperties properties;
    private final MessageSender messageSender;
    private final String prefix = "hls";

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void handleMessage(VideoEvent videoEvent) {
        log.info("S3 endpoint={}, bucket={}, pathStyle={}",
                properties.endpoint(),
                properties.s3().bucket(),
                properties.pathStyle());
        if (videoEvent == null) {
            throw new IllegalStateException("videoEvent is null");
        }
        if (!"upload".equals(videoEvent.type())) {
            log.warn("Ignoring non-upload event: {}", videoEvent);
            return;
        }

        Long videoId = videoEvent.videoId();
        try {
            String url = videoEvent.message();
            // downloading video
            System.out.format("Downloading %s from S3 bucket %s...\n", url, properties.s3().bucket());
            Path orgVideo = getS3Object(properties.s3().bucket(), url);
            // processing it
            Path outputDir = processVideoHLS(orgVideo);
            // upload new video chunks
            uploadS3Objects(outputDir, videoId.toString());

            String message = "hls/" + videoId + '/' + "index.m3u8";
            var response = new VideoEvent(videoId, message, "processed");
            messageSender.sendMessage(RabbitMqConfig.ROUTING_KEY_PROCESSED, response);
        } catch (Exception e) {
            log.error("Failed to process video {}", videoId, e);
            messageSender.sendMessage(
                    RabbitMqConfig.ROUTING_KEY_FAILED,
                    new VideoEvent(videoId, e.getMessage(), "failed")
            );
        }
    }
    // return path of saved file
    private Path getS3Object(String bucket, String key) {
        try{
            Path input = Files.createTempFile("worker-input-", ".mp4");
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(request)) {
                Files.copy(in, input, StandardCopyOption.REPLACE_EXISTING);
                return input;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private Path processVideoHLS(Path originalVideo) throws IOException, InterruptedException {
        Path output = Files.createTempDirectory("worker-output");
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", originalVideo.toString(),
                "-c:v", "libx264",
                "-c:a", "aac",
                "-hls_time", "6",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", output.resolve("segment_%03d.ts").toString(),
                output.resolve("index.m3u8").toString()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        Process process = pb.start();
        boolean finished = process.waitFor(20, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg timed out");
        }

        if (process.exitValue() != 0) {
            throw new IllegalStateException(
                    "ffmpeg failed with exit code " + process.exitValue());
        }
        return output;
    }

    private void uploadS3Objects(Path inputDir, String videoId) throws IOException {
        if (checkFiles(inputDir)){
            try (Stream<Path> files = Files.list(inputDir)) {
                System.out.println("Started processing video");
                files.filter(Files::isRegularFile)
                        .forEach(file -> {
                            System.out.println("Processing file: " + file.getFileName().toString());
                            String fileName = file.getFileName().toString();
                            String objectKey = String.join("/", prefix, videoId, fileName);

                            if(file.getFileName().toString().endsWith(".m3u8")) {
                                this.s3Client.putObject(PutObjectRequest.builder().
                                        bucket(properties.s3().bucket())
                                        .contentType("application/vnd.apple.mpegurl").
                                        key(objectKey).build(), file);
                            }
                            else if(file.getFileName().toString().endsWith(".ts")) {
                                this.s3Client.putObject(PutObjectRequest.builder().
                                        bucket(properties.s3().bucket()).
                                        contentType("video/mp2t").
                                        key(objectKey).build(), file);

                            }
                            else{
                                System.out.println("Skipping file: " + file.getFileName().toString());
                            }
                            // file is one .ts segment or the .m3u8 playlist
                        });
            }
        }
            else{
                throw new IllegalArgumentException("No files found in directory " + inputDir.toString());
            }
        }

    private boolean checkFiles(Path inputDir) throws IOException {
        try (Stream<Path> files = Files.list(inputDir)) {
            var fileList =  files
                    .filter(f -> {
                        String name = f.getFileName().toString();
                        return name.endsWith(".m3u8") || name.endsWith(".ts");
                    })
                    .toList();
            if(fileList.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
