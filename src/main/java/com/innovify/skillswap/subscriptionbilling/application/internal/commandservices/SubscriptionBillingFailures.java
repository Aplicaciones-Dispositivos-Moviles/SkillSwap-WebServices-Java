package com.innovify.skillswap.subscriptionbilling.application.internal.commandservices;

import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;

/** Builds the localized failures of the subscription command service. */
final class SubscriptionBillingFailures {

    private final MessageSource messageSource;

    SubscriptionBillingFailures(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    <T> Result<T> failure(SubscriptionBillingError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }

    /** Persistence failures are database errors; anything else is unexpected. */
    static SubscriptionBillingError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? SubscriptionBillingError.DATABASE_ERROR
                : SubscriptionBillingError.INTERNAL_SERVER_ERROR;
    }
}
