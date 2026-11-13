package com.innovify.skillswap.reputation.application.internal.commandservices;

import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;

/** Builds the localized failures of the reputation command service. */
final class ReputationFailures {

    private final MessageSource messageSource;

    ReputationFailures(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    <T> Result<T> failure(ReputationError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }

    /** Persistence failures are database errors; anything else is unexpected. */
    static ReputationError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? ReputationError.DATABASE_ERROR
                : ReputationError.INTERNAL_SERVER_ERROR;
    }
}
