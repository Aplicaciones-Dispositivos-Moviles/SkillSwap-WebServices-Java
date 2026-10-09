package com.innovify.skillswap.iam.application.internal.commandservices;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Issues the email verification tokens: 256 random bits, sent by email in Base64url and stored only as their
 * SHA-256, so a leaked database does not verify anyone. Each new token replaces the previous one, it expires after
 * {@code tokenTtl} and it is used once (verifying clears it). A new email is sent at most once per
 * {@code resendCooldown} for each account.
 */
public class EmailVerificationIssuer {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;
    private final Duration tokenTtl;
    private final Duration resendCooldown;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationIssuer(UserRepository userRepository, DomainEventPublisher eventPublisher,
                                   Duration tokenTtl, Duration resendCooldown, Clock clock) {
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("The verification token lifetime must be positive.");
        }
        if (resendCooldown == null || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("The verification resend cooldown cannot be negative.");
        }
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.tokenTtl = tokenTtl;
        this.resendCooldown = resendCooldown;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A token just issued: the plain value goes only into the email. */
    public record IssuedToken(String token, Instant issuedAt, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
        }
    }

    /** Sets a new token on the user without saving it; {@link #requestEmail} must follow once it is saved. */
    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(tokenTtl);
        user.issueVerificationToken(hash(token), expiresAt, now);
        return new IssuedToken(token, now, expiresAt);
    }

    /** Asks for the email of a token already saved with the user. */
    public void requestEmail(User savedUser, IssuedToken issued) {
        eventPublisher.publish(new EmailVerificationRequested(savedUser.getId(), savedUser.getUsername().value(),
                savedUser.getEmail().value(), issued.token(), issued.issuedAt(), issued.expiresAt()));
    }

    /**
     * Sends a new verification email to an existing unverified account, unless one was sent within the cooldown.
     *
     * @return whether a new email was requested
     * @throws RuntimeException when the new token cannot be saved (nothing is sent then)
     */
    public boolean reissue(User user) {
        if (!user.canReceiveVerificationEmail(clock.instant(), resendCooldown)) {
            return false;
        }
        IssuedToken issued = issue(user);
        User saved = userRepository.save(user);
        requestEmail(saved, issued);
        return true;
    }

    public Instant now() {
        return clock.instant();
    }

    /** SHA-256 of the token, in lowercase hexadecimal. */
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
