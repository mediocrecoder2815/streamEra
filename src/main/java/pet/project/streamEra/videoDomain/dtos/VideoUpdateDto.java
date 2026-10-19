package pet.project.streamEra.videoDomain.dtos;

import pet.project.streamEra.videoDomain.entity.Category;

import java.util.List;
import java.util.UUID;

public record VideoUpdateDto(
        Long videoId,
        String name,
        List<Category> categoryList,
        String videoUrl,
        String videoStatus
) {
}
