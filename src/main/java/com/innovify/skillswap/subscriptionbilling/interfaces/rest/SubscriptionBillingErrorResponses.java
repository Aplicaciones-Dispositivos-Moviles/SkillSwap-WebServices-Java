package com.innovify.skillswap.subscriptionbilling.interfaces.rest;

import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform.SubscriptionBillingActionResultAssembler;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

/** Errors the controllers detect themselves (ownership, webhook authorization), localized like the services'. */
final class SubscriptionBillingErrorResponses {

    private SubscriptionBillingErrorResponses() {
    }

    static ResponseEntity<?> of(MessageSource messageSource, SubscriptionBillingError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return SubscriptionBillingActionResultAssembler.toError(error, message);
    }
}
