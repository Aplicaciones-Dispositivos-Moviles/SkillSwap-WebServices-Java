package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.DemonstrationEvaluationCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReviewFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitDemonstrationVideoCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationReviewed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import com.innovify.skillswap.credentialverification.application.acl.FileStorageContextFacade;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Video and review of the final demonstration. Like the other command services it is not {@code @Transactional}.
 *
 * <p>The video is validated (size and real type, detected from its first bytes) and stored first, outside any
 * transaction; then, in one transaction with the demonstration locked, it is attached. If that fails the new file is
 * deleted, and when it replaces a previous video the old file is deleted once committed. The review is recorded with
 * the demonstration locked and {@code FinalDemonstrationReviewed} is published once it is committed.
 */
@Service
public class DemonstrationEvaluationCommandServiceImpl implements DemonstrationEvaluationCommandService {

    public static final int MAX_VIDEO_SIZE_BYTES = 25 * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(DemonstrationEvaluationCommandServiceImpl.class);

    private final FinalDemonstrationRepository demonstrationRepository;
    private final FileStorageContextFacade fileStorage;
    private final DomainEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public DemonstrationEvaluationCommandServiceImpl(FinalDemonstrationRepository demonstrationRepository,
                                                     FileStorageContextFacade fileStorage,
                                                     DomainEventPublisher eventPublisher,
                                                     TransactionOperations transactions,
                                                     MessageSource messageSource) {
        this.demonstrationRepository = demonstrationRepository;
        this.fileStorage = fileStorage;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<FinalDemonstration> handle(SubmitDemonstrationVideoCommand command) {
        String storedReference = null;
        try {
            Optional<FinalDemonstration> found = demonstrationRepository.findById(command.demonstrationId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
            }
            if (!found.get().isOwnedBy(command.studentId())) {
                return failures.failure(AssessmentPeerReviewError.NOT_DEMONSTRATION_OWNER);
            }
            if (!found.get().acceptsVideo()) {
                return failures.failure(AssessmentPeerReviewError.DEMONSTRATION_CLOSED);
            }

            AssessmentPeerReviewError fileError = validateVideo(command);
            if (fileError != null) {
                return failures.failure(fileError);
            }
            String contentType = detectContentType(command.content());

            try {
                storedReference = fileStorage.upload(command.content(), contentType,
                        "demonstrations/%d/%s".formatted(command.demonstrationId(),
                                UUID.randomUUID().toString().replace("-", "")));
            } catch (RuntimeException exception) {
                log.error("Could not store the video of the demonstration {}", command.demonstrationId(),
                        exception);
                return failures.failure(AssessmentPeerReviewError.VIDEO_STORAGE_ERROR);
            }

            String newReference = storedReference;
            String[] replaced = new String[1];
            Result<FinalDemonstration> result = transactions.execute(status -> {
                // Read again under the lock: it may have been reviewed or replaced meanwhile.
                Optional<FinalDemonstration> locked = demonstrationRepository.findByIdForUpdate(
                        command.demonstrationId());
                if (locked.isEmpty()) {
                    return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
                }
                FinalDemonstration demonstration = locked.get();
                if (!demonstration.acceptsVideo()) {
                    return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.DEMONSTRATION_CLOSED);
                }
                replaced[0] = demonstration.getVideoStorageReference();
                demonstration.attachVideo(newReference, contentType, command.content().length);
                return Result.success(demonstrationRepository.save(demonstration));
            });

            if (result.isFailure()) {
                deleteQuietly(storedReference);
                return result;
            }
            if (replaced[0] != null) {
                deleteQuietly(replaced[0]);
            }
            return result;
        } catch (RuntimeException exception) {
            log.error("The video of the demonstration {} could not be saved", command.demonstrationId(), exception);
            if (storedReference != null) {
                deleteQuietly(storedReference);
            }
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<FinalDemonstration> handle(ReviewFinalDemonstrationCommand command) {
        try {
            if (demonstrationRepository.findById(command.demonstrationId()).isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
            }

            Result<FinalDemonstration> result = transactions.execute(status -> {
                Optional<FinalDemonstration> locked = demonstrationRepository.findByIdForUpdate(
                        command.demonstrationId());
                if (locked.isEmpty()) {
                    return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
                }
                FinalDemonstration demonstration = locked.get();
                if (!demonstration.isAssignedTo(command.verifierUserId())) {
                    return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.NOT_DEMONSTRATION_VERIFIER);
                }
                if (demonstration.isReviewed()) {
                    return failures.<FinalDemonstration>failure(
                            AssessmentPeerReviewError.DEMONSTRATION_ALREADY_REVIEWED);
                }
                if (!demonstration.isVideoSubmitted()) {
                    return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.VIDEO_NOT_SUBMITTED);
                }

                AssessmentPeerReviewError invalid = validateReview(command);
                if (invalid != null) {
                    return failures.<FinalDemonstration>failure(invalid);
                }
                demonstration.review(command.score(), command.feedback());
                return Result.success(demonstrationRepository.save(demonstration));
            });

            if (result.isSuccess()) {
                FinalDemonstration reviewed = result.value();
                eventPublisher.publish(new FinalDemonstrationReviewed(reviewed.getId(), reviewed.getStudentId(),
                        reviewed.getVerifierUserId(), reviewed.getSkillTag(), reviewed.getScore(),
                        reviewed.getStatus() == DemonstrationStatus.APPROVED));
            }
            return result;
        } catch (RuntimeException exception) {
            log.error("The final demonstration {} could not be reviewed", command.demonstrationId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    private static AssessmentPeerReviewError validateVideo(SubmitDemonstrationVideoCommand command) {
        byte[] content = command.content();
        if (content == null || content.length == 0) {
            return AssessmentPeerReviewError.VIDEO_REQUIRED;
        }
        if (content.length > MAX_VIDEO_SIZE_BYTES) {
            return AssessmentPeerReviewError.VIDEO_TOO_LARGE;
        }

        String detected = detectContentType(content);
        String declared = (command.contentType() == null ? "" : command.contentType()).strip()
                .toLowerCase(Locale.ROOT);
        if (detected == null || !detected.equals(declared)) {
            return AssessmentPeerReviewError.UNSUPPORTED_VIDEO_TYPE;
        }
        return null;
    }

    private static AssessmentPeerReviewError validateReview(ReviewFinalDemonstrationCommand command) {
        Integer score = command.score();
        if (score == null || score < 0 || score > 100) {
            return AssessmentPeerReviewError.INVALID_DEMONSTRATION_SCORE;
        }
        String feedback = command.feedback() == null ? "" : command.feedback().strip();
        if (feedback.length() > FinalDemonstration.MAX_FEEDBACK_LENGTH) {
            return AssessmentPeerReviewError.DEMONSTRATION_FEEDBACK_TOO_LONG;
        }
        if (score < FinalDemonstration.PASSING_SCORE && feedback.isEmpty()) {
            return AssessmentPeerReviewError.DEMONSTRATION_FEEDBACK_REQUIRED;
        }
        return null;
    }

    /** The type of the video from its first bytes: MP4 and QuickTime ("ftyp" box) or WebM (EBML header). */
    static String detectContentType(byte[] content) {
        if (content == null) {
            return null;
        }
        if (content.length >= 12 && content[4] == 'f' && content[5] == 't' && content[6] == 'y'
                && content[7] == 'p') {
            boolean quickTime = content[8] == 'q' && content[9] == 't' && content[10] == ' ' && content[11] == ' ';
            return quickTime ? "video/quicktime" : "video/mp4";
        }
        byte[] ebml = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
        if (content.length >= ebml.length && Arrays.equals(content, 0, ebml.length, ebml, 0, ebml.length)) {
            return "video/webm";
        }
        return null;
    }

    private void deleteQuietly(String storageReference) {
        try {
            fileStorage.delete(storageReference);
        } catch (RuntimeException exception) {
            log.warn("The demonstration video file could not be deleted: {}", storageReference, exception);
        }
    }
}
