package com.innovify.skillswap.recognitionincentives.application.commandservices;

import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateShareableCredentialCommand;
import com.innovify.skillswap.shared.application.Result;

/** Shareable credential command service interface. */
public interface ShareableCredentialCommandService {

    /**
     * Generates the public link of a skill the student verified, or returns the one they already have. Fails with
     * INVALID_SKILL_TAG, SKILL_NOT_VERIFIED, DATABASE_ERROR or INTERNAL_SERVER_ERROR.
     */
    Result<IssuedCredential> handle(CreateShareableCredentialCommand command);
}
