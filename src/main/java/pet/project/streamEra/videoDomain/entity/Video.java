package pet.project.streamEra.videoDomain.entity;


import jakarta.persistence.*;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Entity(name = "videos")
@Getter
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long videoId;

    @Column(name = "name")
    private String name;

    @Column(name = "video_length")
    private Long videoLength;

    @Column(name = "size")
    private Long size;

    @Column(name = "video_url")
    private String videoUrl;

    @ManyToMany
    @JoinTable(
            name = "video_categories",
            joinColumns = @JoinColumn(name = "video_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    Set<Category> categorySet= new HashSet<>();
}
