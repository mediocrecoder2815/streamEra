package pet.project.streamEra.authDomain.dtos;

public record AuthResponseDto(
    String token,
    String username
) {
}
