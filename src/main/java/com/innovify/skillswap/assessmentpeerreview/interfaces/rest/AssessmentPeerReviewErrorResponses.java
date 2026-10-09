package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

/** Errors the controllers detect themselves (ownership, missing resources), localized like the services'. */
final class AssessmentPeerReviewErrorResponses {

    private AssessmentPeerReviewErrorResponses() {
    }

    static ResponseEntity<?> of(MessageSource messageSource, AssessmentPeerReviewError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return AssessmentPeerReviewActionResultAssembler.toError(error, message);
    }
}
