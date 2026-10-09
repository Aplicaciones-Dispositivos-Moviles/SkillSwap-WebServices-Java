package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesActionResultAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

/** Errors the controllers detect themselves (ownership, missing wallet), localized like the services'. */
final class RecognitionIncentivesErrorResponses {

    private RecognitionIncentivesErrorResponses() {
    }

    static ResponseEntity<?> of(MessageSource messageSource, RecognitionIncentivesError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return RecognitionIncentivesActionResultAssembler.toError(error, message);
    }
}
