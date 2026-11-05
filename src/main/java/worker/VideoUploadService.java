package worker;



/*
    Worker service responsible for proccessing/uploading video from client
    Once received message from videoService:
        - downloads video from S3 bucker slices it into chunks, send back to S3
        - sends message videoId and new status once done
*/
public class VideoUploadService {



}
