package com.innovify.skillswap.iam.domain.model.commands;

import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;

/**
 * Sign up command.
 *
 * @param username the desired username
 * @param email    the institutional email (.edu.pe)
 * @param password the plain-text password to hash and store
 * @param role     the role of the new account
 * @param fullName the real name of the student, optional (null when not given)
 */
public record SignUpCommand(String username, String email, String password, UserRole role, String fullName) {

    /** An account without a registered name. */
    public SignUpCommand(String username, String email, String password, UserRole role) {
        this(username, email, password, role, null);
    }
}
