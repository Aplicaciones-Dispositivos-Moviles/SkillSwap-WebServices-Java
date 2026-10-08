package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;

/** Builds the localized failures of the learning path command services. */
final class LearningPathFailures {

    private final MessageSource messageSource;

    LearningPathFailures(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    <T> Result<T> failure(LearningPathError error) {
        return Result.failure(error, message(error));
    }

    <T> Result<T> failure(LearningPathError error, Map<String, Object> details) {
        return Result.failure(error, message(error), details);
    }

    /** Persistence failures are database errors; anything else is unexpected. */
    static LearningPathError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? LearningPathError.DATABASE_ERROR
                : LearningPathError.INTERNAL_SERVER_ERROR;
    }

    private String message(LearningPathError error) {
        String code = ErrorCodes.of(error);
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
