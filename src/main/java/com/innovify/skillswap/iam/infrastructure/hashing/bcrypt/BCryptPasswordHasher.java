package com.innovify.skillswap.iam.infrastructure.hashing.bcrypt;

import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import com.innovify.skillswap.iam.domain.services.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt implementation of {@link PasswordHasher}. Work factor 11, like the C# API (BCrypt.Net), so the
 * hashes already stored in the database keep working.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private static final int STRENGTH = 11;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);

    @Override
    public PasswordHash hashPassword(String plainPassword) {
        return new PasswordHash(encoder.encode(plainPassword));
    }

    @Override
    public boolean verifyPassword(String plainPassword, PasswordHash hash) {
        try {
            return encoder.matches(plainPassword, hash.value());
        } catch (IllegalArgumentException e) {
            // e.g. a password over BCrypt's 72-byte limit can never match
            return false;
        }
    }
}
