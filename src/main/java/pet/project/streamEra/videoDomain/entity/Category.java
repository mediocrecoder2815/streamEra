package pet.project.streamEra.videoDomain.entity;


import jakarta.persistence.*;

@Entity(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer categoryId;

    @Column(name = "name")
    private String name;
}
