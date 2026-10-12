package pet.project.streamEra.videoDomain.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pet.project.streamEra.videoDomain.entity.Category;
import pet.project.streamEra.videoDomain.entity.Video;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Repository

public interface VideoRepository extends JpaRepository<Video, Long> {
    @EntityGraph(attributePaths = {"categorySet"})

    Optional<Video> findByName(String name);

    @Override
    @EntityGraph(attributePaths = {"categorySet"})
    Optional<Video> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"categorySet"})
    List<Video> findAll();

}
