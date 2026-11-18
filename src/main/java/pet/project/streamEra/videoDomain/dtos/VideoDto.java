package pet.project.streamEra.videoDomain.dtos;

import pet.project.streamEra.videoDomain.entity.Category;

import java.util.List;
import java.util.Set;

public record VideoDto(
        String name,
        Long size,
        List<Category> categoryList
) {
}
