package com.innovify.skillswap.iam.infrastructure.tokens.jwt;

import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Generates and validates HS256 JWTs signed with a shared secret. The token carries the user id as
 * {@code sub} plus the {@code username} and {@code role} claims.
 */
@Component
public class JwtTokenGenerator implements TokenGenerator {

    static final int MIN_SECRET_LENGTH = 32;

    private final TokenSettings settings;
    private final SecretKey key;

    public JwtTokenGenerator(TokenSettings settings) {
        if (settings.secret() == null || settings.secret().isBlank()
                || settings.secret().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "token.settings.secret must be configured with at least %d characters."
                            .formatted(MIN_SECRET_LENGTH));
        }
        this.settings = settings;
        this.key = Keys.hmacShaKeyFor(settings.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername().value())
                .claim("role", user.getRole().value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofDays(settings.expirationDays()))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public Optional<Integer> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(Integer.parseInt(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // bad signature, expired, malformed, or a non-numeric subject
            return Optional.empty();
        }
    }
}
