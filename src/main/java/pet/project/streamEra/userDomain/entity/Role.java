package pet.project.streamEra.userDomain.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "roles")
@Getter
@Setter
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short roleId;

    @Column(name = "name", nullable = false)
    private String name;

    @Override
    public String toString() {
        return this.name;
    }
}
