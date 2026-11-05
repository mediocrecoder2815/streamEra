package pet.project.streamEra;


import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pet.project.streamEra.userDomain.dtos.UserUpdateDto;
import pet.project.streamEra.userDomain.entity.Role;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.RoleRepository;
import pet.project.streamEra.userDomain.repository.UserRepository;
import pet.project.streamEra.userDomain.service.UserService;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private UserService userService;

    @BeforeEach
    void setUp(){
        this.userService = new UserService(userRepository, roleRepository);
    }

    /**
     * java.security.Principal is a single-method interface (getName()).
     * A lambda is the simplest possible fake -- no Mockito needed.
     *
     * Equivalent, if you also want to verify interactions:
     *   Principal p = mock(Principal.class);
     *   when(p.getName()).thenReturn("alice");
     */
    private static Principal principal(String username) {
        return () -> username;
    }

    private static User user(String username, String roleName) {
        Role role = new Role();
        role.setName(roleName);

        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername(username);
        user.setRole(role);
        return user;
    }


    @Test
    @DisplayName("findUserById: returns the user when it exists")
    void findUserById_returnsUser() {
        User alice = user("alice", "USER");
        when(userRepository.findById(alice.getUserId())).thenReturn(Optional.of(alice));

        User found = userService.findUserById(alice.getUserId());

        assertThat(found.getUserId()).isEqualTo(alice.getUserId());
        assertThat(found.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findUserById: throws when the user does not exist")
    void findUserById_throws_whenMissing() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findUserById(unknownId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("findUserByUsername: returns the user when it exists")
    void findUserByUsername_returnsUser() {
        User alice = user("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThat(userService.findUserByUsername("alice").getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findUserByUsername: throws when the user does not exist")
    void findUserByUsername_throws_whenMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findUserByUsername("ghost"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("findAllUsers: returns every user from the repository")
    void findAllUsers_returnsAll() {
        User alice = user("alice", "USER");
        User bob = user("bob", "USER");
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));

        assertThat(userService.findAllUsers()).containsExactly(alice, bob);
    }


    @Test
    @DisplayName("updateUser: updates the user identified by the principal")
    void updateUser_updatesUserFromPrincipal() {
        User alice = user("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUser(
                new UserUpdateDto("alice.new", "new-password"),
                principal("alice"));

        // Input side: the same entity loaded from the repo was mutated and saved.
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(alice);
        assertThat(alice.getUsername()).isEqualTo("alice.new");
        assertThat(alice.getPassword()).isEqualTo("new-password");

        // Output side: value assertions.
        assertThat(result.getUsername()).isEqualTo("alice.new");
    }

    @Test
    @DisplayName("updateUser: throws when the principal user no longer exists")
    void updateUser_throws_whenPrincipalUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(
                new UserUpdateDto("x", "y"), principal("ghost")))
                .isInstanceOf(EntityNotFoundException.class);

        verify(userRepository, never()).save(any(User.class));
    }


    @Test
    @DisplayName("deleteUser: a user can delete themselves")
    void deleteUser_allowsSelfDelete() {
        User alice = user("alice", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findById(alice.getUserId())).thenReturn(Optional.of(alice));

        String result = userService.deleteUser(alice.getUserId(), principal("alice"));

        assertThat(result).isEqualTo("Deleted");
        verify(userRepository).delete(alice);
    }

    @Test
    @DisplayName("deleteUser: throws when the target user does not exist")
    void deleteUser_throws_whenTargetMissing() {
        User alice = user("alice", "ADMIN");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(UUID.randomUUID(), principal("alice")))
                .isInstanceOf(EntityNotFoundException.class);

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("deleteUser: throws when the principal user no longer exists")
    void deleteUser_throws_whenPrincipalUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(UUID.randomUUID(), principal("ghost")))
                .isInstanceOf(EntityNotFoundException.class);

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("deleteUser: an admin can delete another user")
    void deleteUser_allowsAdminToDeleteOthers() {
        User admin = user("carol", "ADMIN");
        User bob = user("bob", "USER");
        when(userRepository.findByUsername("carol")).thenReturn(Optional.of(admin));
        when(userRepository.findById(bob.getUserId())).thenReturn(Optional.of(bob));

        String result = userService.deleteUser(bob.getUserId(), principal("carol"));

        assertThat(result).isEqualTo("Deleted");
        verify(userRepository).delete(bob);
    }

    @Test
    @DisplayName("deleteUser: a non-admin cannot delete another user")
    void deleteUser_blocksNonAdminDeletingOthers() {
        User alice = user("alice", "USER");
        User bob = user("bob", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findById(bob.getUserId())).thenReturn(Optional.of(bob));

        String result = userService.deleteUser(bob.getUserId(), principal("alice"));

        assertThat(result).isEqualTo("Not Allowed");
        verify(userRepository, never()).delete(any(User.class));
    }
}
