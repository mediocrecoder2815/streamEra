package pet.project.streamEra.userDomain.service;


import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pet.project.streamEra.userDomain.dtos.UserUpdateDto;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.RoleRepository;
import pet.project.streamEra.userDomain.repository.UserRepository;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public User findUserById(UUID id) {return userRepository.findById(id).orElseThrow(EntityNotFoundException::new);}

    public List<User> findAllUsers(){
        return userRepository.findAll();
    }

    public User findUserByUsername(String username) {return userRepository.findByUsername(username).orElseThrow(EntityNotFoundException::new);}




    // need to also update token, otherwise in case of changing of username, old token becomes invalid
    @Transactional
    public User updateUser(UserUpdateDto user, Principal principal) {
        User userToken = userRepository.findByUsername(principal.getName()).orElseThrow(EntityNotFoundException::new);
        userToken.setUsername(user.username());
        userToken.setPassword(user.password());

        return userRepository.save(userToken);
    }

    public String deleteUser(UUID userID, Principal principal) {
        User userToken = userRepository.findByUsername(principal.getName()).orElseThrow(EntityNotFoundException::new);
        User deletedUser = userRepository.findById(userID).orElseThrow(EntityNotFoundException::new);
        if(!userToken.getUsername().equals(deletedUser.getUsername()) && userToken.getRole().getName().equals("ADMIN")) {
            return "Not Allowed";
        }
        userRepository.delete(deletedUser);
        return "Deleted";
    }
}
