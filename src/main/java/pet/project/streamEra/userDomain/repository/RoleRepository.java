package pet.project.streamEra.userDomain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pet.project.streamEra.userDomain.entity.Role;


@Repository
public interface RoleRepository extends JpaRepository<Role, Short> {
}
