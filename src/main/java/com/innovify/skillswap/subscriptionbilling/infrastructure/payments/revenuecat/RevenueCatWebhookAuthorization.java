package com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat;

import com.innovify.skillswap.subscriptionbilling.application.internal.outboundservices.WebhookAuthorizationVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Compares the Authorization header with the value configured in the RevenueCat dashboard. Both are hashed first
 * and compared in constant time, so the time of the answer reveals neither the secret nor its length.
 */
public class RevenueCatWebhookAuthorization implements WebhookAuthorizationVerifier {

    private final byte[] expectedDigest;

    /** @param expectedAuthorization the configured value; null or blank rejects every request */
    public RevenueCatWebhookAuthorization(String expectedAuthorization) {
        this.expectedDigest = expectedAuthorization == null || expectedAuthorization.isBlank()
                ? null
                : digest(expectedAuthorization.strip());
    }

    @Override
    public boolean isAuthorized(String authorizationHeader) {
        if (expectedDigest == null || authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, digest(authorizationHeader.strip()));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
