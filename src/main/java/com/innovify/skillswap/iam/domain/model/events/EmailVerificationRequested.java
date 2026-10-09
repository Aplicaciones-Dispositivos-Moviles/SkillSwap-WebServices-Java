package com.innovify.skillswap.iam.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.time.Instant;

/**
 * A verification email must be sent to an account (after the sign-up, or again when the student asks for it or
 * tries to sign in before verifying). The event lives only in memory, in process: the token is never stored in
 * plain text, so this is the only place that carries it until the email is sent.
 *
 * @param userId    the account
 * @param username  the username, to greet the student
 * @param email     the institutional email the message is sent to
 * @param token     the verification token (secret: it is the content of the link)
 * @param issuedAt  when the token was issued
 * @param expiresAt when the token stops being valid
 */
public record EmailVerificationRequested(int userId, String username, String email, String token,
                                         Instant issuedAt, Instant expiresAt) implements DomainEvent {

    /** Keeps the token out of the logs. */
    @Override
    public String toString() {
        return "EmailVerificationRequested[userId=" + userId + ", expiresAt=" + expiresAt + "]";
    }
}
