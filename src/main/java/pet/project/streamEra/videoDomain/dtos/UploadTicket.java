package pet.project.streamEra.videoDomain.dtos;

public record UploadTicket(
        Long videoId,
        String objectKey,
        String uploadUrl,
        String contentType,
        int expiresInSeconds
) {
}
