package pet.project.streamEra.userDomain.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;
import java.util.UUID;

@Entity
@Setter
@Table(name = "watch_history", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "video_id"})
})
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

    @Column(name = "playback_position_seconds")
    @ColumnDefault(value = "0")
    private Long timestamp;
}
