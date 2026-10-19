package pet.project.streamEra.playbackDomain.entity;

import java.time.Instant;
import java.util.UUID;

public record WatchHistoryDto(
        UUID videoId,
        Instant date,
        String timestamp
) {
}
