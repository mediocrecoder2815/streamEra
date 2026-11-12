package pet.project.streamEra.userDomain.dtos;

import java.time.Instant;
import java.util.UUID;

public record WatchHistoryDto(
        Long videoId,
        Instant date,
        Long timestamp
) {
}
