package pet.project.streamEra.userDomain.contoller;


import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pet.project.streamEra.userDomain.dtos.UserUpdateDto;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.service.UserService;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
@AllArgsConstructor
public class UserController {
    private final UserService userService;


    @QueryMapping(name= "users")
    public List<User> users()
    {
        return userService.findAllUsers();
    }

    @QueryMapping(name = "user")
    public User user(@Argument UUID userId){
        return userService.findUserById(userId);
    }

    @MutationMapping
    public User updateUser(@Argument UserUpdateDto user, Principal principal) {
        return userService.updateUser(user, principal);
    }
    @MutationMapping
    public String deleteUser(@Argument UUID userId, Principal principal) {
        return userService.deleteUser(userId, principal);
    }

}
