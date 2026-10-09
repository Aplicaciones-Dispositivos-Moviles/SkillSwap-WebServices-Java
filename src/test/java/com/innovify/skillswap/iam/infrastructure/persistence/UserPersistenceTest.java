package com.innovify.skillswap.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class UserPersistenceTest extends PostgresIntegrationTest {

    @Autowired
    private UserRepository repository;

    @Test
    void repository_findsAndChecksUsersThroughTheValueObjects() {
        repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));

        assertThat(repository.findByUsername(new Username("ANA"))).isPresent();
        assertThat(repository.findByEmail(new Email("Ana@UPC.edu.pe"))).isPresent();
        assertThat(repository.existsByUsername(new Username("ana"))).isTrue();
        assertThat(repository.existsByEmail(new Email("ana@upc.edu.pe"))).isTrue();
        assertThat(repository.existsByUsername(new Username("nobody"))).isFalse();
        assertThat(repository.existsByEmail(new Email("nobody@upc.edu.pe"))).isFalse();
    }

    @Test
    void verificationToken_isStoredAndFoundByItsHash() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        User user = TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT);
        user.issueVerificationToken("b".repeat(64), now.plusSeconds(3600), now);
        repository.save(user);

        User found = repository.findByVerificationTokenHash("b".repeat(64)).orElseThrow();
        assertThat(found.getUsername().value()).isEqualTo("ana");
        assertThat(found.getVerificationTokenExpiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(found.getVerificationEmailSentAt()).isEqualTo(now);
        assertThat(repository.findByVerificationTokenHash("c".repeat(64))).isEmpty();

        found.verify();
        repository.save(found);
        assertThat(repository.findByVerificationTokenHash("b".repeat(64))).isEmpty();
    }

    @Test
    void save_assignsTheIdAndFindByIdReturnsTheUser() {
        User saved = repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(repository.findById(saved.getId())).get()
                .extracting(user -> user.getUsername().value()).isEqualTo("ana");
        assertThat(repository.findById(saved.getId() + 100)).isEmpty();
    }

    @Test
    void database_rejectsTwoUsersWithTheSameUsername() {
        repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));

        assertThatThrownBy(() -> repository.save(TestData.newUser("ana", "other@upc.edu.pe", UserRole.STUDENT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_rejectsTwoUsersWithTheSameEmail() {
        repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));

        assertThatThrownBy(() -> repository.save(TestData.newUser("other", "ana@upc.edu.pe", UserRole.STUDENT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deviceToken_isPersistedWhenPresentAndNullWhenAbsent() {
        User withToken = TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT);
        withToken.registerDeviceToken("device-abc");
        repository.save(withToken);
        repository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));

        User ana = repository.findByUsername(new Username("ana")).orElseThrow();
        User bob = repository.findByUsername(new Username("bob")).orElseThrow();

        assertThat(ana.getDeviceToken()).isEqualTo(new DeviceToken("device-abc"));
        assertThat(bob.getDeviceToken()).isNull();
    }

    @Test
    void role_isStoredAsTheTextTheCSharpApiWrote() throws Exception {
        repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));

        assertThat(queryString("SELECT role FROM users WHERE username = 'ana'")).isEqualTo("Student");
        assertThat(repository.findByUsername(new Username("ana")).orElseThrow().getRole())
                .isEqualTo(UserRole.STUDENT);
    }

    @Test
    void readsARowInsertedByTheCSharpApi() throws Exception {
        execute("INSERT INTO users (username, email, password_hash, role, is_verified, bio) "
                + "VALUES ('legacy', 'legacy@upc.edu.pe', '$2a$11$hash', 'Student', true, 'hi')");

        User user = repository.findByUsername(new Username("legacy")).orElseThrow();

        assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(user.isVerified()).isTrue();
        assertThat(user.getBio()).isEqualTo("hi");
        assertThat(user.getDeviceToken()).isNull();
    }

    @Test
    void savingAnAlreadyLoadedUser_updatesTheRow() throws Exception {
        User saved = repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));

        User loaded = repository.findById(saved.getId()).orElseThrow();
        loaded.updateBio("Backend developer");
        repository.save(loaded);

        assertThat(queryString("SELECT bio FROM users WHERE id = " + saved.getId())).isEqualTo("Backend developer");
        assertThat(queryString("SELECT count(*) FROM users")).isEqualTo("1");
    }
}
