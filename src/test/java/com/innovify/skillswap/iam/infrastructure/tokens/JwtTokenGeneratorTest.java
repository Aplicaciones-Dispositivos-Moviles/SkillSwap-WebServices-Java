package com.innovify.skillswap.iam.infrastructure.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.infrastructure.tokens.jwt.JwtTokenGenerator;
import com.innovify.skillswap.iam.infrastructure.tokens.jwt.TokenSettings;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenGeneratorTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters-long!";
    private static final String OTHER_SECRET = "another-secret-with-at-least-32-characters!";

    private static JwtTokenGenerator generator(String secret) {
        return new JwtTokenGenerator(new TokenSettings(secret, 7));
    }

    private static User userWithId(int id) {
        User user = TestData.newUser();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void validateToken_withTokenFromTheSameSecret_returnsTheUserId() {
        JwtTokenGenerator generator = generator(SECRET);

        String token = generator.generateToken(userWithId(7));

        assertThat(generator.validateToken(token)).contains(7);
    }

    @Test
    void validateToken_withTokenSignedByAnotherSecret_returnsEmpty() {
        String token = generator(OTHER_SECRET).generateToken(userWithId(7));

        assertThat(generator(SECRET).validateToken(token)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-jwt"})
    void validateToken_withInvalidToken_returnsEmpty(String token) {
        assertThat(generator(SECRET).validateToken(token)).isEmpty();
    }

    @Test
    void validateToken_withNullToken_returnsEmpty() {
        assertThat(generator(SECRET).validateToken(null)).isEmpty();
    }

    @Test
    void validateToken_withTamperedToken_returnsEmpty() {
        JwtTokenGenerator generator = generator(SECRET);
        String token = generator.generateToken(userWithId(7));
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThat(generator.validateToken(tampered)).isEmpty();
    }

    @Test
    void validateToken_withAnExpiredToken_returnsEmpty() {
        JwtTokenGenerator expired = new JwtTokenGenerator(new TokenSettings(SECRET, -1));

        String token = expired.generateToken(userWithId(7));

        assertThat(expired.validateToken(token)).isEmpty();
    }

    @Test
    void generateToken_carriesTheUsernameAndTheRoleClaims() {
        User coordinator = TestData.newUser("root", "root@upc.edu.pe", UserRole.COORDINATOR);
        ReflectionTestUtils.setField(coordinator, "id", 3);

        String token = generator(SECRET).generateToken(coordinator);

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"3\"", "\"username\":\"root\"", "\"role\":\"Coordinator\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "too-short"})
    void constructor_withMissingOrShortSecret_throws(String secret) {
        assertThatThrownBy(() -> generator(secret)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_withNullSecret_throws() {
        assertThatThrownBy(() -> generator(null)).isInstanceOf(IllegalStateException.class);
    }
}
