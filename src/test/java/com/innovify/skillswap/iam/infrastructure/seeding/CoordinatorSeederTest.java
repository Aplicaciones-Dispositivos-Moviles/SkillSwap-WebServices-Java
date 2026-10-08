package com.innovify.skillswap.iam.infrastructure.seeding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.application.fakes.FakePasswordHasher;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordinatorSeederTest {

    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final FakeUserRepository users = new FakeUserRepository();

    private CoordinatorSeeder seeder(String username, String email, String password) {
        return new CoordinatorSeeder(users, hasher, new CoordinatorSeedSettings(username, email, password));
    }

    private CoordinatorSeeder validSeeder() {
        return seeder("Root", "root@upc.edu.pe", "password123");
    }

    @Test
    void seed_withAValidConfiguration_createsTheCoordinator() {
        CoordinatorSeedOutcome outcome = validSeeder().seed();

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.CREATED);
        assertThat(users.users()).hasSize(1);
        User coordinator = users.users().get(0);
        assertThat(coordinator.getRole()).isEqualTo(UserRole.COORDINATOR);
        assertThat(coordinator.getUsername().value()).isEqualTo("root");
        assertThat(coordinator.getEmail().value()).isEqualTo("root@upc.edu.pe");
        assertThat(hasher.verifyPassword("password123", coordinator.getPasswordHash())).isTrue();
        assertThat(users.saveCalls()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,null,null", "'',' ',''"}, nullValues = "null")
    void seed_withoutAnyValue_doesNothing(String username, String email, String password) {
        CoordinatorSeedOutcome outcome = seeder(username, email, password).seed();

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.NOT_CONFIGURED);
        assertThat(users.users()).isEmpty();
        assertThat(users.saveCalls()).isZero();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "root,null,password123",
            "null,root@upc.edu.pe,password123",
            "root,root@upc.edu.pe,null"}, nullValues = "null")
    void seed_withAPartialConfiguration_isInvalid(String username, String email, String password) {
        CoordinatorSeedOutcome outcome = seeder(username, email, password).seed();

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.INVALID);
        assertThat(users.users()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "ro,root@upc.edu.pe,password123",
            "'ro ot',root@upc.edu.pe,password123",
            "root,root@gmail.com,password123",
            "root,root@upc.edu.pe,short12"})
    void seed_withInvalidValues_isInvalid(String username, String email, String password) {
        CoordinatorSeedOutcome outcome = seeder(username, email, password).seed();

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.INVALID);
        assertThat(users.users()).isEmpty();
    }

    @Test
    void seed_withAPasswordOver72Bytes_isInvalid() {
        CoordinatorSeedOutcome outcome = seeder("root", "root@upc.edu.pe", "a".repeat(73)).seed();

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.INVALID);
        assertThat(users.users()).isEmpty();
    }

    @Test
    void seed_whenTheUsernameExists_leavesTheAccountUntouched() {
        users.save(new User(new Username("root"), new Email("other@upc.edu.pe"),
                new PasswordHash("whatever"), UserRole.STUDENT));
        int savesBefore = users.saveCalls();

        CoordinatorSeedOutcome outcome = validSeeder().seed();

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.ALREADY_EXISTS);
        assertThat(users.users()).hasSize(1);
        assertThat(users.users().get(0).getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(users.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void seed_whenTheEmailExists_leavesTheAccountUntouched() {
        users.save(TestData.newUser("someone", "root@upc.edu.pe", UserRole.STUDENT));

        CoordinatorSeedOutcome outcome = validSeeder().seed();

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.ALREADY_EXISTS);
        assertThat(users.users()).hasSize(1);
    }

    @Test
    void seed_runTwice_createsTheCoordinatorOnlyOnce() {
        validSeeder().seed();

        CoordinatorSeedOutcome second = validSeeder().seed();

        assertThat(second).isEqualTo(CoordinatorSeedOutcome.ALREADY_EXISTS);
        assertThat(users.users()).hasSize(1);
    }

    @Test
    void seed_whenPersistenceFails_stopsTheStartup() {
        users.failOnSave(new IllegalStateException("database down"));

        assertThatThrownBy(() -> validSeeder().seed()).isInstanceOf(IllegalStateException.class);
    }
}
