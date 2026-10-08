package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AttachCaseEvidenceCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ResolveVerificationCaseCommand;
import com.innovify.skillswap.shared.application.Result;

/** Verification case command service interface. */
public interface VerificationCaseCommandService {

    /** The student of the case attaches the link to their repository or portfolio. */
    Result<VerificationCase> handle(AttachCaseEvidenceCommand command);

    /** The assigned verifier resolves the case; an approval completes the node. */
    Result<VerificationCase> handle(ResolveVerificationCaseCommand command);
}
