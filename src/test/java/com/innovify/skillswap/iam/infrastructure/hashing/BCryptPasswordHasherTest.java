package com.innovify.skillswap.iam.infrastructure.hashing;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.infrastructure.hashing.bcrypt.BCryptPasswordHasher;
import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashPassword_doesNotStoreThePlainText() {
        PasswordHash hash = hasher.hashPassword("password123");

        assertThat(hash.value()).isNotEqualTo("password123");
        assertThat(hash.value()).startsWith("$2");
    }

    @Test
    void verifyPassword_withCorrectPassword_returnsTrue() {
        PasswordHash hash = hasher.hashPassword("password123");

        assertThat(hasher.verifyPassword("password123", hash)).isTrue();
    }

    @Test
    void verifyPassword_withWrongPassword_returnsFalse() {
        PasswordHash hash = hasher.hashPassword("password123");

        assertThat(hasher.verifyPassword("password124", hash)).isFalse();
    }

    @Test
    void hashPassword_usesARandomSaltPerHash() {
        assertThat(hasher.hashPassword("password123")).isNotEqualTo(hasher.hashPassword("password123"));
    }

    @Test
    void verifyPassword_withMalformedHash_returnsFalse() {
        assertThat(hasher.verifyPassword("password123", new PasswordHash("not-a-hash"))).isFalse();
    }

    @Test
    void verifyPassword_acceptsAnExistingBcryptHashWithWorkFactor11() {
        // Fixed "$2a$11$" hash of "password123", the format BCrypt.Net wrote in the existing database.
        PasswordHash existing = new PasswordHash("$2a$11$M2jy94mXfznvLQ.iBVr4ge9KZ7cJPW4dSxSXA7HA.p6Z6E8.3ze7y");

        assertThat(hasher.verifyPassword("password123", existing)).isTrue();
        assertThat(hasher.verifyPassword("password124", existing)).isFalse();
    }
}
