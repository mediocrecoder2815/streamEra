package pet.project.streamEra.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pet.project.streamEra.userDomain.entity.Role;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        var header =  request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer "))
        {
            doFilter(request, response, filterChain);
            return;
        }
        try{
            String token = header.substring(7);
            if(jwtService.extractUsername(token) != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if(jwtService.isTokenValid(token, jwtService.extractUsername(token))){
                    String username = jwtService.extractUsername(token);
                    var role = jwtService.extractAllClaims(token).get("roles");
                    SimpleGrantedAuthority sga = new  SimpleGrantedAuthority(role.toString());
                    var ctx = SecurityContextHolder.getContext();
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            username, null, List.of(sga));
                    ctx.setAuthentication(auth);
                }
            }
        }
        catch (IllegalArgumentException e){
            log.error("Bad credentials");
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
