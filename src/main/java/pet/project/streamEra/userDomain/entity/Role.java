package pet.project.streamEra.userDomain.entity;


import jakarta.persistence.*;
import lombok.Getter;

@Entity(name = "roles")
@Getter

public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short roleId;

    @Column(name = "name", nullable = false)
    private String name;
}
