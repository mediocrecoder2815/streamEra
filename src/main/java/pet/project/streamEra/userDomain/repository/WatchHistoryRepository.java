package pet.project.streamEra.userDomain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pet.project.streamEra.userDomain.entity.WatchHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, UUID> {
    List<WatchHistory> findByUserId(UUID userId);

    Optional<WatchHistory> findByUserIdAndVideoId(UUID userId, Long videoId);
}