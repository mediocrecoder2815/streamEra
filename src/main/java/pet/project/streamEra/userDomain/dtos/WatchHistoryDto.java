package pet.project.streamEra.userDomain.dtos;

import java.time.Instant;
import java.util.UUID;

public record WatchHistoryDto(
        UUID videoId,
        Instant date,
        Long timestamp
) {
}
