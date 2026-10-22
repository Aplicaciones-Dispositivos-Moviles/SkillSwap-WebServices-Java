package com.innovify.skillswap.recognitionincentives.domain.services;

/** Contract for the tokens of the public links of the credentials. */
public interface CredentialTokenGenerator {

    /** A new random token, {@code ShareableCredential.TOKEN_LENGTH} characters of base64url. */
    String generate();
}
