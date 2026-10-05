package pet.project.streamEra.authDomain.dtos;

public record AuthResponse(
    String token,
    String username
) {
}
