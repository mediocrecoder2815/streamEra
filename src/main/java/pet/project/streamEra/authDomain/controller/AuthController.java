package pet.project.streamEra.authDomain.controller;


import lombok.AllArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pet.project.streamEra.authDomain.dtos.AuthRequestDto;
import pet.project.streamEra.authDomain.dtos.AuthResponseDto;
import pet.project.streamEra.authDomain.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody AuthRequestDto requestDto){
        return ResponseEntity.ok(authService.login(requestDto));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@RequestBody AuthRequestDto requestDto) throws BadRequestException {
        return ResponseEntity.ok(authService.register(requestDto));
    }
}
