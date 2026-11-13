package com.innovify.skillswap.iam;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;

/** Shared builders for the IAM tests. */
public final class TestData {

    private TestData() {
    }

    public static User newUser() {
        return newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT);
    }

    public static User newUser(String username, String email, UserRole role) {
        return new User(new Username(username), new Email(email), new PasswordHash("hashed-password"), role);
    }
}
