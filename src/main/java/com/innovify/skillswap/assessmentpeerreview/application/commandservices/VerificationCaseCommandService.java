package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AppealVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AttachCaseEvidenceCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ResolveVerificationCaseCommand;
import com.innovify.skillswap.shared.application.Result;

/** Verification case command service interface. */
public interface VerificationCaseCommandService {

    /** The student of the case attaches the link to their repository or portfolio. */
    Result<VerificationCase> handle(AttachCaseEvidenceCommand command);

    /** The assigned verifier resolves the case; an approval completes the node. */
    Result<VerificationCase> handle(ResolveVerificationCaseCommand command);

    /**
     * The student of a rejected case appeals it. The case reopens and goes to the least loaded verifier who is
     * neither the student nor the one who rejected it; with nobody available it waits as pending.
     */
    Result<VerificationCase> handle(AppealVerificationCaseCommand command);
}
