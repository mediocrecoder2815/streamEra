package pet.project.streamEra.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pet.project.streamEra.userDomain.entity.Role;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    private String secret;

    private SecretKey key;

    private long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                        @Value("${app.jwt.expirationMs}") long expirationMs){
        this.secret = secret;
        this.expirationMs = expirationMs;
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }


    // generates key, based on username and roles
    public String generateToken(String username, String role) {
        if(username==null || username.isEmpty() || role==null){
            throw new IllegalArgumentException("username or roles cannot be null or empty");
        }
        var expiration = Date.from(Instant.now().plusMillis(expirationMs));
        return Jwts.builder().
                signWith(key).
                subject(username).
                claim("roles", role).
                expiration(expiration).
                issuedAt(Date.from(Instant.now())).
                compact();
    }

    // extracts all claims, using jwt util, verifies with secret key
    public Claims extractAllClaims(String token){
        if (token == null || token.isEmpty()){
            throw new IllegalArgumentException("token cannot be null or empty");
        }
        return Jwts.parser().
                verifyWith(key).
                build().
                parseSignedClaims(token).
                getPayload();
    }

    public String extractUsername(String token){
        if (token == null || token.isEmpty()){
            throw new IllegalArgumentException("token cannot be null or empty");
        }
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        if (token == null || username == null) {
            throw new IllegalArgumentException("Wrong input");
        }
        var expDate = this.extractAllClaims(token).getExpiration();
        return this.extractUsername(token).equals(username) &&
                expDate.after(Date.from(Instant.now()));
    }

}
