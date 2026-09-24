package pet.project.streamEra.userDomain.contoller;


import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.web.bind.annotation.RestController;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.UserRepository;

import java.util.List;

@RestController
@AllArgsConstructor
public class UserController {
    private final UserRepository userRepository;


    @QueryMapping(name = "users")
    public List<User> getUsers(){
        return userRepository.findAll();
    }
}
