package com.innovify.skillswap.reputation.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.reputation.application.commandservices.ReputationCommandService;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.commands.RecordAutomaticApprovalCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordCaseResolutionCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordOverturnCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.AssessmentAttemptPassed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.shared.application.Result;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReputationEventHandlersTest {

    private final RecordingReputationCommandService service = new RecordingReputationCommandService();

    private static VerificationCaseResolved resolved(ReviewDecision decision, Integer overturned) {
        return new VerificationCaseResolved(10, 1, 2, 5, "http-basics", decision, overturned);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED,true", "REJECTED,false"})
    void caseResolved_becomesACommandWithTheDecision(ReviewDecision decision, boolean approved) {
        new RecordCaseResolutionEventHandler(service).handle(resolved(decision, null));

        assertThat(service.resolutions).containsExactly(new RecordCaseResolutionCommand(2, 1, approved));
        assertThat(service.overturns).isEmpty();
        assertThat(service.approvals).isEmpty();
    }

    @Test
    void caseResolved_withAnOverturnedVerifier_alsoRecordsTheOverturn() {
        new RecordCaseResolutionEventHandler(service).handle(resolved(ReviewDecision.APPROVED, 7));

        assertThat(service.resolutions).containsExactly(new RecordCaseResolutionCommand(2, 1, true));
        assertThat(service.overturns).containsExactly(new RecordOverturnCommand(7));
    }

    @Test
    void caseResolved_whenTheResolutionFails_stillRecordsTheOverturnAndDoesNotThrow() {
        service.fail = true;

        new RecordCaseResolutionEventHandler(service).handle(resolved(ReviewDecision.APPROVED, 7));

        assertThat(service.resolutions).hasSize(1);
        assertThat(service.overturns).hasSize(1);
    }

    @Test
    void attemptPassed_becomesAnAutomaticApprovalOfTheStudent() {
        new RecordAutomaticApprovalEventHandler(service).handle(
                new AssessmentAttemptPassed(7, 1, 5, "http-basics"));

        assertThat(service.approvals).containsExactly(new RecordAutomaticApprovalCommand(1));
        assertThat(service.resolutions).isEmpty();
    }

    @Test
    void attemptPassed_whenTheServiceFails_doesNotThrow() {
        service.fail = true;

        new RecordAutomaticApprovalEventHandler(service).handle(
                new AssessmentAttemptPassed(7, 1, 5, "http-basics"));

        assertThat(service.approvals).hasSize(1);
    }

    private static final class RecordingReputationCommandService implements ReputationCommandService {

        final List<RecordCaseResolutionCommand> resolutions = new ArrayList<>();
        final List<RecordOverturnCommand> overturns = new ArrayList<>();
        final List<RecordAutomaticApprovalCommand> approvals = new ArrayList<>();
        boolean fail;

        @Override
        public Result<VerifierReliability> handle(RecordCaseResolutionCommand command) {
            resolutions.add(command);
            return fail ? Result.failure(ReputationError.DATABASE_ERROR, "failure")
                    : Result.success(new VerifierReliability(command.verifierUserId()));
        }

        @Override
        public Result<VerifierReliability> handle(RecordOverturnCommand command) {
            overturns.add(command);
            return fail ? Result.failure(ReputationError.DATABASE_ERROR, "failure")
                    : Result.success(new VerifierReliability(command.verifierUserId()));
        }

        @Override
        public Result<StudentEmployabilityScore> handle(RecordAutomaticApprovalCommand command) {
            approvals.add(command);
            return fail ? Result.failure(ReputationError.DATABASE_ERROR, "failure")
                    : Result.success(new StudentEmployabilityScore(command.studentId()));
        }
    }
}
