package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;

/** Builds the localized failures of the assessment and peer review command services. */
final class AssessmentPeerReviewFailures {

    private final MessageSource messageSource;

    AssessmentPeerReviewFailures(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    <T> Result<T> failure(AssessmentPeerReviewError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }

    /** Persistence failures are database errors; anything else is unexpected. */
    static AssessmentPeerReviewError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? AssessmentPeerReviewError.DATABASE_ERROR
                : AssessmentPeerReviewError.INTERNAL_SERVER_ERROR;
    }

    /** A node that could not be completed is a database error only when Learning Path Engine failed to save. */
    static AssessmentPeerReviewError fromNodeCompletion(NodeCompletionOutcome outcome) {
        return outcome == NodeCompletionOutcome.FAILED
                ? AssessmentPeerReviewError.DATABASE_ERROR
                : AssessmentPeerReviewError.NODE_NOT_AVAILABLE;
    }
}
