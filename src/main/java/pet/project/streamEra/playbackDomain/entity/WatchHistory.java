package pet.project.streamEra.playbackDomain.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "watch_history")
@Setter
@Getter
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

    @Column(name = "timestamp")
    @ColumnDefault(value = "00:00:00")
    private String timestamp;
}
