package com.innovify.skillswap.iam.infrastructure.verification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Email verification settings from {@code app.verification.*} (environment: APP_VERIFICATION_BASE_URL,
 * APP_VERIFICATION_TOKEN_TTL, APP_VERIFICATION_RESEND_COOLDOWN).
 *
 * @param baseUrl        public URL the link of the email points to; the link is
 *                       {@code <baseUrl>/api/v1/authentication/verify-email?token=...}
 * @param tokenTtl       how long a verification link is valid (24h by default)
 * @param resendCooldown minimum time between two verification emails to the same account
 */
@ConfigurationProperties(prefix = "app.verification")
public record EmailVerificationSettings(
        @DefaultValue(EmailVerificationSettings.DEFAULT_BASE_URL) String baseUrl,
        @DefaultValue("24h") Duration tokenTtl,
        @DefaultValue("2m") Duration resendCooldown) {

    public static final String DEFAULT_BASE_URL = "https://skillswap-webservices-java.onrender.com";

    public EmailVerificationSettings {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.strip();
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("The setting app.verification.base-url must be an http(s) URL.");
        }
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("The setting app.verification.token-ttl must be positive.");
        }
        if (resendCooldown == null || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("The setting app.verification.resend-cooldown cannot be negative.");
        }
    }
}
