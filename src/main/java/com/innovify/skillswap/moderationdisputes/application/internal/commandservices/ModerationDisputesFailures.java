package com.innovify.skillswap.moderationdisputes.application.internal.commandservices;

import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;

/** Builds the localized failures of the dispute command service. */
final class ModerationDisputesFailures {

    private final MessageSource messageSource;

    ModerationDisputesFailures(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    <T> Result<T> failure(ModerationDisputesError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }

    /** Persistence failures are database errors; anything else is unexpected. */
    static ModerationDisputesError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? ModerationDisputesError.DATABASE_ERROR
                : ModerationDisputesError.INTERNAL_SERVER_ERROR;
    }
}
