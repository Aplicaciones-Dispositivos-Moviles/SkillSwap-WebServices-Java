package com.innovify.skillswap.moderationdisputes.interfaces.rest;

import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.transform.ModerationDisputesActionResultAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

/** Errors the controllers detect themselves (access, missing resources), localized like the services'. */
final class ModerationDisputesErrorResponses {

    private ModerationDisputesErrorResponses() {
    }

    static ResponseEntity<?> of(MessageSource messageSource, ModerationDisputesError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return ModerationDisputesActionResultAssembler.toError(error, message);
    }
}
