package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReinstateVerifierCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SuspendVerifierCommand;
import com.innovify.skillswap.shared.application.Result;

/** Verifier suspension command service interface. */
public interface VerifierSuspensionCommandService {

    /**
     * A Verificador senior suspends a verifier whose reliability is below 60: they receive no more cases, and the
     * cases they had in progress go back to Pending and are given to other verifiers (no missed deadline is
     * counted). Fails with NOT_SENIOR_VERIFIER, JUSTIFICATION_REQUIRED, JUSTIFICATION_TOO_LONG,
     * CANNOT_SUSPEND_SELF, VERIFIER_PROFILE_NOT_FOUND, ALREADY_SUSPENDED, NOT_LOW_RELIABILITY, DATABASE_ERROR or
     * INTERNAL_SERVER_ERROR.
     */
    Result<VerifierProfile> handle(SuspendVerifierCommand command);

    /**
     * A Verificador senior reinstates a suspended verifier, who can receive cases again; the pending cases of their
     * skills are given out. Fails with NOT_SENIOR_VERIFIER, VERIFIER_PROFILE_NOT_FOUND, NOT_SUSPENDED,
     * DATABASE_ERROR or INTERNAL_SERVER_ERROR.
     */
    Result<VerifierProfile> handle(ReinstateVerifierCommand command);
}
