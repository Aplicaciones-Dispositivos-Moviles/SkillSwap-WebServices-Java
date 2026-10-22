package com.innovify.skillswap.recognitionincentives.domain.services;

import java.security.SecureRandom;
import java.util.Base64;

/** 256 random bits from a cryptographically secure generator, so a token cannot be guessed. */
public class SecureRandomCredentialTokenGenerator implements CredentialTokenGenerator {

    private static final int BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
