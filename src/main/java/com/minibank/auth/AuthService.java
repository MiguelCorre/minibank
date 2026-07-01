package com.minibank.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
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
import com.minibank.common.error.InvalidRefreshTokenException;

@Service
public class AuthService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    public record TokenPair(String accessToken, String refreshToken, User user) {
    }

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final LoginRateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       LoginRateLimiter rateLimiter) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyUsedException(normalized);
        }
        return users.save(User.register(normalized, passwordEncoder.encode(rawPassword), displayName));
    }

    @Transactional
    public TokenPair login(String email, String rawPassword) {
        String normalized = normalize(email);
        rateLimiter.checkAllowed(normalized);
        User user = users.findByEmail(normalized)
                .filter(found -> passwordEncoder.matches(rawPassword, found.getPasswordHash()))
                .orElseThrow(() -> {
                    rateLimiter.recordFailure(normalized);
                    return new InvalidCredentialsException();
                });
        rateLimiter.reset(normalized);
        return issueTokenPair(user);
    }

    /**
     * Rotates the refresh token: the presented token is revoked and a new
     * pair is issued. Presenting an already-revoked token is treated as
     * theft — every active token of that user is revoked; noRollbackFor
     * keeps that revocation committed while the request still fails.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (stored.isRevoked()) {
            refreshTokens.revokeAllForUser(stored.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (stored.isExpired(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        stored.revoke();
        User user = users.findById(stored.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(sha256(rawRefreshToken)).ifPresent(RefreshToken::revoke);
    }

    private TokenPair issueTokenPair(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(RefreshToken.issue(
                user.getId(), sha256(rawRefreshToken), Instant.now().plus(REFRESH_TOKEN_TTL)));
        return new TokenPair(issueAccessToken(user), rawRefreshToken, user);
    }

    private String issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getEmail())
                .claim("name", user.getDisplayName())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String normalize(String email) {
        return email.toLowerCase(Locale.ROOT).strip();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
