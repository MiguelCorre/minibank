package com.minibank.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.common.error.EmailAlreadyUsedException;
import com.minibank.common.error.InvalidCredentialsException;

@Service
public class AuthService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName) {
        String normalized = email.toLowerCase(Locale.ROOT).strip();
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyUsedException(normalized);
        }
        return users.save(User.register(normalized, passwordEncoder.encode(rawPassword), displayName));
    }

    @Transactional(readOnly = true)
    public String login(String email, String rawPassword) {
        User user = users.findByEmail(email.toLowerCase(Locale.ROOT).strip())
                .filter(found -> passwordEncoder.matches(rawPassword, found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    }

    private String issueToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getEmail())
                .claim("name", user.getDisplayName())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
