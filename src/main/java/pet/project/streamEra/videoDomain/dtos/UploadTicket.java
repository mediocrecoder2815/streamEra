package pet.project.streamEra.videoDomain.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@Validated
public record UploadTicket(
       @NotNull Long videoId,
       @NotNull @NotBlank String objectKey,
       @NotNull @NotBlank String uploadUrl,
       @NotNull @NotBlank String contentType,
        int expiresInSeconds
) {
}
