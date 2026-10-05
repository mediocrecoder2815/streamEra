package pet.project.streamEra.authDomain.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import pet.project.streamEra.authDomain.dtos.AuthRequestDto;
import pet.project.streamEra.authDomain.dtos.AuthResponseDto;
import pet.project.streamEra.security.JwtService;
import pet.project.streamEra.userDomain.entity.Role;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.RoleRepository;
import pet.project.streamEra.userDomain.repository.UserRepository;

import java.util.List;
import java.util.Optional;



@Service
@AllArgsConstructor
public class AuthService {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthResponseDto login(AuthRequestDto requestDto) {
        var username = requestDto.username();
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            throw new BadCredentialsException("User not found");
        }
        if(passwordEncoder.matches(requestDto.password(), user.get().getPassword())) {
            String token = jwtService.generateToken(requestDto.username(), user.get().getRole().getName());
            return new AuthResponseDto(token, user.get().getUsername());
        }
        else{
            throw new BadCredentialsException("Wrong password or no");
        }
    }

    public AuthResponseDto register(AuthRequestDto requestDto) throws BadRequestException {
        User newUser = new User();
        Optional<User> usernameTest = userRepository.findByUsername(requestDto.username());
        if(usernameTest.isPresent()){
            throw new BadRequestException("Username is already in use");
        }
        newUser.setUsername(requestDto.username());
        var hashedPassword = requestDto.password();
        hashedPassword = passwordEncoder.encode(hashedPassword);
        newUser.setPassword(hashedPassword);
        Role role = roleRepository.findById((short)1).orElseThrow(EntityNotFoundException::new);
        newUser.setRole(role);
        userRepository.save(newUser);
        String token = jwtService.generateToken(newUser.getUsername(), role.getName());
        return  new AuthResponseDto(token, newUser.getUsername());
    }


}
