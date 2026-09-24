package pet.project.streamEra.userDomain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pet.project.streamEra.userDomain.entity.User;

import java.util.UUID;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
}
