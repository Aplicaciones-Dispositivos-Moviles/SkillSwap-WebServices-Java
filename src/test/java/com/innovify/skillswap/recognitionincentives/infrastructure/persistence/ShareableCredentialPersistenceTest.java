package com.innovify.skillswap.recognitionincentives.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.repositories.ShareableCredentialRepository;
import com.innovify.skillswap.recognitionincentives.domain.services.CredentialTokenGenerator;
import com.innovify.skillswap.recognitionincentives.domain.services.SecureRandomCredentialTokenGenerator;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ShareableCredentialPersistenceTest extends PostgresIntegrationTest {

    private final CredentialTokenGenerator generator = new SecureRandomCredentialTokenGenerator();

    @Autowired
    private ShareableCredentialRepository credentials;

    private ShareableCredential save(int ownerId, String skillTag, Instant createdAt) {
        return credentials.save(new ShareableCredential(ownerId, skillTag, generator.generate(), createdAt));
    }

    @Test
    void credential_isStoredAndFoundByTokenAndByOwnerAndSkill() {
        Instant createdAt = Instant.parse("2026-10-22T10:00:00Z");
        ShareableCredential saved = save(3, "java-basics", createdAt);

        ShareableCredential byToken = credentials.findByToken(saved.getToken()).orElseThrow();

        assertThat(byToken.getId()).isNotNull().isEqualTo(saved.getId());
        assertThat(byToken.getOwnerId()).isEqualTo(3);
        assertThat(byToken.getSkillTag()).isEqualTo("java-basics");
        assertThat(byToken.getCreatedAt()).isEqualTo(createdAt);
        assertThat(credentials.findByOwnerIdAndSkillTag(3, "java-basics")).isPresent();
    }

    @Test
    void credential_findsNothingForAnUnknownTokenOrSkill() {
        save(3, "java-basics", Instant.now());

        assertThat(credentials.findByToken("x".repeat(43))).isEmpty();
        assertThat(credentials.findByOwnerIdAndSkillTag(3, "sql-basics")).isEmpty();
        assertThat(credentials.findByOwnerIdAndSkillTag(4, "java-basics")).isEmpty();
    }

    @Test
    void credential_rejectsASecondOneForTheSameOwnerAndSkill() {
        save(3, "java-basics", Instant.now());

        assertThatThrownBy(() -> save(3, "java-basics", Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(save(4, "java-basics", Instant.now())).isNotNull();
    }

    @Test
    void credential_rejectsARepeatedToken() {
        ShareableCredential first = save(3, "java-basics", Instant.now());

        assertThatThrownBy(() -> credentials.save(
                new ShareableCredential(4, "sql-basics", first.getToken(), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void credentials_ofAnOwnerAreListedNewestFirst() {
        save(3, "java-basics", Instant.parse("2026-10-20T10:00:00Z"));
        save(3, "sql-basics", Instant.parse("2026-10-22T10:00:00Z"));
        save(3, "git-basics", Instant.parse("2026-10-21T10:00:00Z"));
        save(4, "java-basics", Instant.parse("2026-10-23T10:00:00Z"));

        assertThat(credentials.findByOwnerId(3)).extracting(ShareableCredential::getSkillTag)
                .containsExactly("sql-basics", "git-basics", "java-basics");
        assertThat(credentials.findByOwnerId(99)).isEmpty();
    }
}
