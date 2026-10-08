package com.innovify.skillswap.iam.infrastructure.seeding;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.application.commandservices.UserCommandService;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.iam.domain.services.PasswordHasher;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CoordinatorSeedIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private UserCommandService commandService;

    private CoordinatorSeedOutcome seed(String username, String email, String password) {
        return new CoordinatorSeeder(repository, hasher, new CoordinatorSeedSettings(username, email, password))
                .seed();
    }

    @Test
    void seed_createsACoordinatorWhoCanSignIn() {
        CoordinatorSeedOutcome outcome = seed("root", "root@upc.edu.pe", "password123");

        assertThat(outcome).isEqualTo(CoordinatorSeedOutcome.CREATED);
        var signIn = commandService.handle(new SignInCommand("root", "password123"));
        assertThat(signIn.isSuccess()).isTrue();
        assertThat(signIn.value().user().getRole()).isEqualTo(UserRole.COORDINATOR);
        assertThat(signIn.value().token()).isNotBlank();
        assertThat(repository.findByUsername(new Username("root"))).isPresent();
    }

    @Test
    void seed_runTwice_keepsASingleAccount() throws Exception {
        seed("root", "root@upc.edu.pe", "password123");

        CoordinatorSeedOutcome second = seed("root", "root@upc.edu.pe", "password123");

        assertThat(second).isEqualTo(CoordinatorSeedOutcome.ALREADY_EXISTS);
        assertThat(queryString("SELECT count(*) FROM users")).isEqualTo("1");
    }
}
