package worker;

import java.util.Set;

public record VideoEvent(Long videoId, String message, String type) {

    private static final Set<String> VALID_TYPES =
            Set.of("upload", "failed", "processed");

    public VideoEvent {
        if (type == null || !VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown video event type: " + type);
        }
    }
}