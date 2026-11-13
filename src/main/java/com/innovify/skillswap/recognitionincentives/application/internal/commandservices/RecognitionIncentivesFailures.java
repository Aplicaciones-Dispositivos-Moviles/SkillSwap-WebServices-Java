package com.innovify.skillswap.recognitionincentives.application.internal.commandservices;

import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;

/** Builds the localized failures of the wallet command service. */
final class RecognitionIncentivesFailures {

    private final MessageSource messageSource;

    RecognitionIncentivesFailures(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    <T> Result<T> failure(RecognitionIncentivesError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }

    /** Persistence failures are database errors; anything else is unexpected. */
    static RecognitionIncentivesError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? RecognitionIncentivesError.DATABASE_ERROR
                : RecognitionIncentivesError.INTERNAL_SERVER_ERROR;
    }
}
