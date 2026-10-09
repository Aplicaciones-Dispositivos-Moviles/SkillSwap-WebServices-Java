package com.innovify.skillswap.reputation.interfaces.rest;

import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.interfaces.rest.transform.ReputationActionResultAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

/** Errors the controllers detect themselves (ownership, missing reputation), localized like the services'. */
final class ReputationErrorResponses {

    private ReputationErrorResponses() {
    }

    static ResponseEntity<?> of(MessageSource messageSource, ReputationError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return ReputationActionResultAssembler.toError(error, message);
    }
}
