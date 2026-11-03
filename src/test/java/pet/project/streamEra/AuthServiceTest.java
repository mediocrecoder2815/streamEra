package pet.project.streamEra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pet.project.streamEra.authDomain.dtos.AuthInput;
import pet.project.streamEra.authDomain.dtos.AuthResponse;
import pet.project.streamEra.authDomain.service.AuthService;
import pet.project.streamEra.security.JwtService;
import pet.project.streamEra.userDomain.entity.Role;
import pet.project.streamEra.userDomain.entity.User;
import pet.project.streamEra.userDomain.repository.RoleRepository;
import pet.project.streamEra.userDomain.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UNIT test example for AuthService.
 *
 * The idea of a unit test:
 *   - you test ONE class in isolation (here: AuthService)
 *   - everything that class depends on (repositories, JwtService) is a MOCK
 *   - no Spring context, no database, no HTTP -> the test runs in milliseconds
 *
 * @ExtendWith(MockitoExtension.class) makes Mockito scan this class for
 * @Mock fields and create the fakes for each test.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // A fake UserRepository. It doesn't talk to Postgres at all;
    // you decide what findById / findByUsername / save return.
    @Mock
    private UserRepository userRepository;

    // Another fake dependency.
    @Mock
    private RoleRepository roleRepository;

    // Another fake dependency: we don't want to generate real JWTs in this test.
    @Mock
    private JwtService jwtService;

    // The real class under test.
    private AuthService authService;

    // A real BCrypt encoder: we want real hashing in the register test.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        // Explicit construction. You don't need @InjectMocks to write a unit test;
        // calling the constructor yourself is often clearer.
        //
        // Note: AuthService's @AllArgsConstructor also takes the BCryptPasswordEncoder,
        // because it is a field. Pass the real one here so hashing behaves normally.
        authService = new AuthService(jwtService, userRepository, roleRepository, encoder);
    }

    @Test
    @DisplayName("login: returns token and username when credentials are correct")
    void login_returnsTokenAndUsername_whenCredentialsAreCorrect() {
        // ---------- ARRANGE (set up the world of this test) ----------
        Role role = mock(Role.class);
        when(role.getName()).thenReturn("USER");

        User user = new User();
        user.setUsername("alice");
        user.setPassword(encoder.encode("correct-password"));
        user.setRole(role);

        // "When findByUsername('alice') is called, return this user."
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        // "When a token is requested for alice with role USER, return 'fake-token'."
        when(jwtService.generateToken("alice", "USER")).thenReturn("fake-token");

        AuthInput input = new AuthInput("alice", "correct-password");

        // ---------- ACT (call exactly one method) ----------
        AuthResponse response = authService.login(input);

        // ---------- ASSERT (check the observable result) ----------
        assertThat(response.token()).isEqualTo("fake-token");
        assertThat(response.username()).isEqualTo("alice");

        // Optional but useful: verify the interaction that matters.
        verify(jwtService).generateToken("alice", "USER");
    }

    @Test
    @DisplayName("login: throws BadCredentialsException when the user does not exist")
    void login_throws_whenUserDoesNotExist() {
        // ARRANGE
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThatThrownBy(() -> authService.login(new AuthInput("ghost", "whatever")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("User not found");

        // A token must never be created if the user wasn't found.
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("login: throws BadCredentialsException when the password is wrong")
    void login_throws_whenPasswordIsWrong() {
        // ARRANGE
        User user = new User();
        user.setUsername("alice");
        user.setPassword(encoder.encode("correct-password"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        // ACT + ASSERT
        assertThatThrownBy(() -> authService.login(new AuthInput("alice", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("register: saves the user with a hashed password and returns a token")
    void register_savesHashedPasswordAndReturnsToken() throws Exception {
        // ARRANGE
        // Role has no setters, so it is easiest to mock it here.
        Role role = mock(Role.class);
        when(role.getName()).thenReturn("USER");

        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(roleRepository.findById((short) 1)).thenReturn(Optional.of(role));
        when(jwtService.generateToken("bob", "USER")).thenReturn("new-token");

        // ACT
        AuthResponse response = authService.register(new AuthInput("bob", "plain-password"));

        // ASSERT: returned data
        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.username()).isEqualTo("bob");

        // ASSERT: what was actually persisted.
        // ArgumentCaptor lets you inspect the object that was passed to save().
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());

        User persisted = savedUser.getValue();
        assertThat(persisted.getUsername()).isEqualTo("bob");
        // The password must be hashed, not stored as plain text.
        assertThat(persisted.getPassword()).isNotEqualTo("plain-password");
        assertThat(encoder.matches("plain-password", persisted.getPassword())).isTrue();
        assertThat(persisted.getRole()).isSameAs(role);
    }

    @Test
    @DisplayName("register: throws BadRequestException when the username is taken")
    void register_throws_whenUsernameAlreadyExists() {
        // ARRANGE
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));

        // ACT + ASSERT
        assertThatThrownBy(() -> authService.register(new AuthInput("alice", "whatever")))
                .isInstanceOf(org.apache.coyote.BadRequestException.class)
                .hasMessageContaining("already in use");

        // Nothing should be written to the database.
        verify(userRepository, never()).save(any(User.class));
    }
}
