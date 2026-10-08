package com.innovify.skillswap.iam.infrastructure.seeding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account of the Coordinator created at startup ({@code seed.coordinator.*}; environment:
 * SEED_COORDINATOR_USERNAME, SEED_COORDINATOR_EMAIL, SEED_COORDINATOR_PASSWORD). Coordinators cannot sign up,
 * so this is how the first one exists in a hosted environment.
 *
 * @param username the username
 * @param email    must be an institutional (.edu.pe) address, like every account
 * @param password at least 8 characters and at most 72 bytes
 */
@ConfigurationProperties(prefix = "seed.coordinator")
public record CoordinatorSeedSettings(String username, String email, String password) {
}
