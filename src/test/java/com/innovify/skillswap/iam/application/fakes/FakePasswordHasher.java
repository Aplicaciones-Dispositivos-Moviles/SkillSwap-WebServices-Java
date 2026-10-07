package com.innovify.skillswap.iam.application.fakes;

import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import com.innovify.skillswap.iam.domain.services.PasswordHasher;

public class FakePasswordHasher implements PasswordHasher {

    @Override
    public PasswordHash hashPassword(String plainPassword) {
        return new PasswordHash("hashed:" + plainPassword);
    }

    @Override
    public boolean verifyPassword(String plainPassword, PasswordHash hash) {
        return hash.value().equals("hashed:" + plainPassword);
    }
}
