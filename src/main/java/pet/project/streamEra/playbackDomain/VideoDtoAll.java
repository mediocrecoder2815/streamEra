package pet.project.streamEra.playbackDomain;

import pet.project.streamEra.videoDomain.entity.Category;

import java.util.List;

public record VideoDtoAll(
        String name,
        Long videoLength,
        List<Category> categorySet
){
}
