package pet.project.streamEra.authDomain.controller;


import lombok.AllArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import pet.project.streamEra.authDomain.dtos.AuthInput;
import pet.project.streamEra.authDomain.dtos.AuthResponse;
import pet.project.streamEra.authDomain.service.AuthService;

@Controller
@AllArgsConstructor
public class AuthController {
    private final AuthService authService;

    @MutationMapping
    public AuthResponse login(@Argument AuthInput requestDto){
        return authService.login(requestDto);
    }

    @MutationMapping
    public AuthResponse register(@Argument AuthInput requestDto) throws BadRequestException {
        return authService.register(requestDto);
    }
}
