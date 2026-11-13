package com.innovify.skillswap.iam.application.commandservices;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.ResendVerificationEmailCommand;
import com.innovify.skillswap.iam.domain.model.commands.VerifyEmailCommand;
import com.innovify.skillswap.shared.application.Result;

/** Email verification command service interface. */
public interface EmailVerificationCommandService {

    /** Verifies the account that owns the token. @return the verified user */
    Result<User> handle(VerifyEmailCommand command);

    /**
     * Sends a new verification email when the account exists, is not verified and the cooldown has passed. It
     * always succeeds, so the answer does not reveal which emails are registered.
     */
    Result<Void> handle(ResendVerificationEmailCommand command);
}
