package pet.project.streamEra.userDomain.dtos;

import java.util.UUID;

public record WatchHistoryProgressDto(
        UUID watchHistoryId,
        Long timestamp
) {
}
