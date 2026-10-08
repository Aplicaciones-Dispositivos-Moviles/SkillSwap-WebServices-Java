package com.innovify.skillswap.iam.infrastructure.seeding;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.iam.domain.services.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * Creates the Coordinator account described by {@link CoordinatorSeedSettings} when it does not exist yet.
 * A missing or malformed configuration is reported in the log and never stops the API; a persistence
 * failure does.
 */
@Component
public class CoordinatorSeeder {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorSeeder.class);

    // The same rule as the sign-up: at least 8 characters, and BCrypt ignores anything beyond 72 bytes.
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final CoordinatorSeedSettings settings;

    public CoordinatorSeeder(UserRepository userRepository, PasswordHasher passwordHasher,
                             CoordinatorSeedSettings settings) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.settings = settings;
    }

    public CoordinatorSeedOutcome seed() {
        long provided = Stream.of(settings.username(), settings.email(), settings.password())
                .filter(value -> value != null && !value.isBlank())
                .count();

        if (provided == 0) {
            return CoordinatorSeedOutcome.NOT_CONFIGURED;
        }

        if (provided < 3
                || !Username.isValid(settings.username())
                || !Email.isValid(settings.email())
                || !isAcceptablePassword(settings.password())) {
            log.error("The Coordinator seed is not valid and was skipped: it needs a username, an institutional "
                    + "(.edu.pe) email and a password of at least {} characters.", MIN_PASSWORD_LENGTH);
            return CoordinatorSeedOutcome.INVALID;
        }

        Username username = new Username(settings.username());
        Email email = new Email(settings.email());

        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            log.info("An account for the Coordinator seed {} already exists; it was left untouched.",
                    username.value());
            return CoordinatorSeedOutcome.ALREADY_EXISTS;
        }

        userRepository.save(new User(username, email, passwordHasher.hashPassword(settings.password()),
                UserRole.COORDINATOR));

        log.info("The Coordinator account {} was created.", username.value());
        return CoordinatorSeedOutcome.CREATED;
    }

    private static boolean isAcceptablePassword(String password) {
        return password != null
                && password.length() >= MIN_PASSWORD_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}
