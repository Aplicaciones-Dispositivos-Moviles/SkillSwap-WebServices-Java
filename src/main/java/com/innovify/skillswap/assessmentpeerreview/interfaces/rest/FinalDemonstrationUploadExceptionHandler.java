package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * A request over the multipart limit (25 MB, application.properties) is rejected by the framework before the
 * controller runs. It is answered with the same 413 VideoTooLarge error as a video over 25 MB.
 */
@RestControllerAdvice(assignableTypes = FinalDemonstrationsController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FinalDemonstrationUploadExceptionHandler {

    private final MessageSource messageSource;

    public FinalDemonstrationUploadExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.VIDEO_TOO_LARGE);
    }
}
