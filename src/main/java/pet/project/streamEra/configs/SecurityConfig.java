package pet.project.streamEra.configs;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import pet.project.streamEra.security.JwtAuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthentiactionFillter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http.
                csrf(AbstractHttpConfigurer::disable).
                authorizeHttpRequests(
                        auth -> auth.
                        requestMatchers("/**/graphql").permitAll().
                        requestMatchers("/api/auth/**").permitAll().
                        anyRequest().hasAnyRole("ADMIN", "USER"))
                .addFilterBefore(jwtAuthentiactionFillter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();

    }
}
