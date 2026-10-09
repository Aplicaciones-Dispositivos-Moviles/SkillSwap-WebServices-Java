package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps application results and domain errors to HTTP responses. */
public final class LearningPathActionResultAssembler {

    private LearningPathActionResultAssembler() {
    }

    public static HttpStatus toStatusFromError(LearningPathError error) {
        return switch (error) {
            case INVALID_GOAL, NONE -> HttpStatus.BAD_REQUEST;
            case GOAL_NOT_INTERPRETABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case GOAL_ALREADY_ACHIEVED, PLAN_LIMIT_REACHED, PATH_NOT_ACTIVE, PATH_NOT_PAUSED, PATH_PAUSED, NODE_LOCKED,
                 NODE_ALREADY_COMPLETED, OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case PATH_NOT_FOUND, NODE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_PATH_OWNER -> HttpStatus.FORBIDDEN;
            case QUESTION_GENERATION_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static <T> ResponseEntity<?> toResponse(Result<T> result, Function<T, ResponseEntity<?>> onSuccess) {
        if (result.isSuccess()) {
            return onSuccess.apply(result.value());
        }
        LearningPathError error = (LearningPathError) result.error();
        Map<String, Object> details = result.details() == null ? Map.of() : result.details();
        return ProblemDetails.of(toStatusFromError(error), error, result.message(), details);
    }

    public static ResponseEntity<?> toError(LearningPathError error, String localizedMessage) {
        return ProblemDetails.of(toStatusFromError(error), error, localizedMessage);
    }
}
