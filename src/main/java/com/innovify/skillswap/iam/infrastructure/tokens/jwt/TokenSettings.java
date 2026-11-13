package com.innovify.skillswap.iam.infrastructure.tokens.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT settings from {@code token.settings.*} (environment: TOKEN_SETTINGS_SECRET,
 * TOKEN_SETTINGS_EXPIRATION_DAYS).
 *
 * @param secret         shared secret used to sign tokens (at least 32 characters)
 * @param expirationDays token lifetime in days
 */
@ConfigurationProperties(prefix = "token.settings")
public record TokenSettings(String secret, @DefaultValue("7") int expirationDays) {
}
