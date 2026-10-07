package com.innovify.skillswap.iam.domain.services;

import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;

/** Contract for hashing and verifying passwords, decoupling the domain from the concrete algorithm. */
public interface PasswordHasher {

    PasswordHash hashPassword(String plainPassword);

    boolean verifyPassword(String plainPassword, PasswordHash hash);
}
