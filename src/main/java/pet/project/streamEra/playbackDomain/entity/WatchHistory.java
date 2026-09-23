package pet.project.streamEra.playbackDomain.entity;


import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "watch_history")
@DynamicInsert
public class WatchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID watchHistoryId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Column(name= "date")
    private Instant date;
}
