package pet.project.streamEra.authDomain.dtos;


import jakarta.validation.constraints.NotBlank;

public record AuthInput(
        @NotBlank String username,
        @NotBlank String password
) {
}
