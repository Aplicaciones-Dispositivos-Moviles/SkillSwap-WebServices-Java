package com.innovify.skillswap.iam.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

/**
 * Resource for signing up a new account. Any other member sent by the client (e.g. a "role") is ignored.
 *
 * @param username the desired username
 * @param email    the institutional email (.edu.pe)
 * @param password the plain-text password (8 to 72 characters)
 * @param fullName the real name of the student (optional, up to 150 characters); the holder of the certificates
 *                 they upload is compared with it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SignUpResource(@NotNull String username, @NotNull String email, @NotNull String password,
                             String fullName) {

    public SignUpResource(String username, String email, String password) {
        this(username, email, password, null);
    }
}
