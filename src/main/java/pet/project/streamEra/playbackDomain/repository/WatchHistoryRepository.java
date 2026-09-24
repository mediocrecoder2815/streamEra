package pet.project.streamEra.playbackDomain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pet.project.streamEra.playbackDomain.entity.WatchHistory;

import java.util.UUID;


@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, UUID> {
}