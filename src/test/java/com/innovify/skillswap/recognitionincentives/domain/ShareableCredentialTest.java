package com.innovify.skillswap.recognitionincentives.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.services.CredentialTokenGenerator;
import com.innovify.skillswap.recognitionincentives.domain.services.SecureRandomCredentialTokenGenerator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ShareableCredentialTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private final CredentialTokenGenerator generator = new SecureRandomCredentialTokenGenerator();

    @Test
    void constructor_keepsTheDataAndStripsTheSkill() {
        String token = generator.generate();

        ShareableCredential credential = new ShareableCredential(3, "  java-basics ", token, NOW);

        assertThat(credential.getOwnerId()).isEqualTo(3);
        assertThat(credential.getSkillTag()).isEqualTo("java-basics");
        assertThat(credential.getToken()).isEqualTo(token);
        assertThat(credential.getCreatedAt()).isEqualTo(NOW);
        assertThat(credential.getId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_withAnInvalidOwner_throwsDomainException(int ownerId) {
        assertThatThrownBy(() -> new ShareableCredential(ownerId, "java-basics", generator.generate(), NOW))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void constructor_withoutSkill_throwsDomainException(String skillTag) {
        assertThatThrownBy(() -> new ShareableCredential(3, skillTag, generator.generate(), NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withASkillThatIsTooLong_throwsDomainException() {
        String tooLong = "a".repeat(ShareableCredential.MAX_SKILL_TAG_LENGTH + 1);

        assertThatThrownBy(() -> new ShareableCredential(3, tooLong, generator.generate(), NOW))
                .isInstanceOf(DomainException.class);
        assertThat(new ShareableCredential(3, "a".repeat(ShareableCredential.MAX_SKILL_TAG_LENGTH),
                generator.generate(), NOW)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "has spaces has spaces has spaces has spaces ", "ñ234567890123456789012345678901234567890123"})
    void constructor_withAnInvalidToken_throwsDomainException(String token) {
        assertThatThrownBy(() -> new ShareableCredential(3, "java-basics", token, NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withoutDate_throwsNullPointerException() {
        assertThatThrownBy(() -> new ShareableCredential(3, "java-basics", generator.generate(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void generator_producesUrlSafeTokensOfTheExpectedLengthThatDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = generator.generate();
            assertThat(token).hasSize(ShareableCredential.TOKEN_LENGTH).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }

        assertThat(tokens).hasSize(1000);
    }
}
