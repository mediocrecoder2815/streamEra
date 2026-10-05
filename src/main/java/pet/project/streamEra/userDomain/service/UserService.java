package pet.project.streamEra.userDomain.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.RoleRepository;
import pet.project.streamEra.userDomain.repository.UserRepository;

import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public User findUserById(UUID id) {return userRepository.findById(id).orElseThrow(EntityNotFoundException::new);}

    public User findUserByUsername(String username) {return userRepository.findByUsername(username).orElseThrow(EntityNotFoundException::new);}



}
