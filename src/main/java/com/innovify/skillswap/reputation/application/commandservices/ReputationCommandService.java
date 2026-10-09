package com.innovify.skillswap.reputation.application.commandservices;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.commands.RecordAutomaticApprovalCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordCaseResolutionCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordOverturnCommand;
import com.innovify.skillswap.shared.application.Result;

/** Reputation command service interface. Nobody rates anyone: these commands come from domain events. */
public interface ReputationCommandService {

    /**
     * A verifier resolved a case: it counts in their reliability, and an approval certifies a skill of the
     * student. The rating of the verifier profile follows the new reliability.
     */
    Result<VerifierReliability> handle(RecordCaseResolutionCommand command);

    /** An appeal overturned the rejection of a verifier, which discounts their reliability. */
    Result<VerifierReliability> handle(RecordOverturnCommand command);

    /** A student passed an assessment without a verifier, which certifies the skill. */
    Result<StudentEmployabilityScore> handle(RecordAutomaticApprovalCommand command);
}
