package pet.project.streamEra.userDomain.contoller;


import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Controller
@AllArgsConstructor
public class UserController {
    private final UserRepository userRepository;


    @QueryMapping(name= "users")
    public List<User> users(){
        return userRepository.findAll();
    }

    @QueryMapping(name = "user")
    public User user(@Argument UUID userId){
        return userRepository.findById(userId).get();
    }
}
