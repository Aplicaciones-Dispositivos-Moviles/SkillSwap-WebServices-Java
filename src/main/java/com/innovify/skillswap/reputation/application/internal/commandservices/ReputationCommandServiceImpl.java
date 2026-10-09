package com.innovify.skillswap.reputation.application.internal.commandservices;

import com.innovify.skillswap.reputation.application.commandservices.ReputationCommandService;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.commands.RecordAutomaticApprovalCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordCaseResolutionCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordMissedDeadlineCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordOverturnCommand;
import com.innovify.skillswap.reputation.domain.repositories.StudentEmployabilityScoreRepository;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.services.EmployabilityScoreCalculator;
import com.innovify.skillswap.reputation.domain.services.VerifierReliabilityCalculator;
import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.shared.application.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Reputation command service. Not {@code @Transactional}: the records it changes are saved together in the
 * given {@link TransactionOperations}, which must be a new transaction because the commands come from event
 * handlers that run after the commit of the transaction that raised the event.
 */
public class ReputationCommandServiceImpl implements ReputationCommandService {

    private static final Logger log = LoggerFactory.getLogger(ReputationCommandServiceImpl.class);

    private final VerifierReliabilityRepository reliabilityRepository;
    private final StudentEmployabilityScoreRepository employabilityRepository;
    private final VerifierReliabilityCalculator reliabilityCalculator;
    private final EmployabilityScoreCalculator employabilityCalculator;
    private final VerifierProfileContextFacade verifierProfileFacade;
    private final TransactionOperations transactions;
    private final ReputationFailures failures;

    public ReputationCommandServiceImpl(VerifierReliabilityRepository reliabilityRepository,
                                        StudentEmployabilityScoreRepository employabilityRepository,
                                        VerifierReliabilityCalculator reliabilityCalculator,
                                        EmployabilityScoreCalculator employabilityCalculator,
                                        VerifierProfileContextFacade verifierProfileFacade,
                                        @Qualifier("reputationTransactions") TransactionOperations transactions,
                                        MessageSource messageSource) {
        this.reliabilityRepository = reliabilityRepository;
        this.employabilityRepository = employabilityRepository;
        this.reliabilityCalculator = reliabilityCalculator;
        this.employabilityCalculator = employabilityCalculator;
        this.verifierProfileFacade = verifierProfileFacade;
        this.transactions = transactions;
        this.failures = new ReputationFailures(messageSource);
    }

    /** What a command saved. */
    private record Saved(VerifierReliability reliability, StudentEmployabilityScore employability) {
    }

    @Override
    public Result<VerifierReliability> handle(RecordCaseResolutionCommand command) {
        try {
            VerifierReliability reliability = reliabilityRepository.findByVerifierUserId(command.verifierUserId())
                    .orElseGet(() -> new VerifierReliability(command.verifierUserId()));
            reliability.recordResolution(reliabilityCalculator);

            StudentEmployabilityScore employability = null;
            if (command.approved()) {
                employability = employabilityRepository.findByStudentId(command.studentId())
                        .orElseGet(() -> new StudentEmployabilityScore(command.studentId()));
                employability.recordSkillVerified(employabilityCalculator);
            }

            VerifierReliability changedReliability = reliability;
            StudentEmployabilityScore changedEmployability = employability;
            Saved saved = transactions.execute(status -> new Saved(
                    reliabilityRepository.save(changedReliability),
                    changedEmployability == null ? null : employabilityRepository.save(changedEmployability)));

            syncRating(saved.reliability());
            return Result.success(saved.reliability());
        } catch (RuntimeException exception) {
            log.error("Could not record the resolution of the verifier {}", command.verifierUserId(), exception);
            return failures.failure(ReputationFailures.toError(exception));
        }
    }

    @Override
    public Result<VerifierReliability> handle(RecordOverturnCommand command) {
        try {
            VerifierReliability reliability = reliabilityRepository.findByVerifierUserId(command.verifierUserId())
                    .orElseGet(() -> new VerifierReliability(command.verifierUserId()));
            reliability.recordOverturn(reliabilityCalculator);

            VerifierReliability saved = transactions.execute(status -> reliabilityRepository.save(reliability));

            syncRating(saved);
            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not record the overturned decision of the verifier {}", command.verifierUserId(),
                    exception);
            return failures.failure(ReputationFailures.toError(exception));
        }
    }

    @Override
    public Result<VerifierReliability> handle(RecordMissedDeadlineCommand command) {
        try {
            VerifierReliability reliability = reliabilityRepository.findByVerifierUserId(command.verifierUserId())
                    .orElseGet(() -> new VerifierReliability(command.verifierUserId()));
            reliability.recordMissedDeadline(reliabilityCalculator);

            VerifierReliability saved = transactions.execute(status -> reliabilityRepository.save(reliability));

            syncRating(saved);
            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not record the missed deadline of the verifier {}", command.verifierUserId(),
                    exception);
            return failures.failure(ReputationFailures.toError(exception));
        }
    }

    @Override
    public Result<StudentEmployabilityScore> handle(RecordAutomaticApprovalCommand command) {
        try {
            StudentEmployabilityScore employability = employabilityRepository.findByStudentId(command.studentId())
                    .orElseGet(() -> new StudentEmployabilityScore(command.studentId()));
            employability.recordSkillVerified(employabilityCalculator);

            StudentEmployabilityScore saved = transactions.execute(status -> employabilityRepository.save(employability));
            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not record the automatic approval of the student {}", command.studentId(), exception);
            return failures.failure(ReputationFailures.toError(exception));
        }
    }

    /** The rating of the profile follows the reliability; a failure here never undoes the reputation. */
    private void syncRating(VerifierReliability reliability) {
        try {
            verifierProfileFacade.updateRating(reliability.getVerifierUserId(), reliability.getScore().value());
        } catch (RuntimeException exception) {
            log.warn("Could not sync the rating of the verifier {}", reliability.getVerifierUserId(), exception);
        }
    }
}
