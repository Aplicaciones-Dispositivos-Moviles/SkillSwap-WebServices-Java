package com.innovify.skillswap.recognitionincentives.interfaces.rest.transform;

import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps application results and domain errors to HTTP responses. */
public final class RecognitionIncentivesActionResultAssembler {

    private RecognitionIncentivesActionResultAssembler() {
    }

    public static HttpStatus toStatusFromError(RecognitionIncentivesError error) {
        return switch (error) {
            case NONE, INVALID_REDEMPTION_ITEM -> HttpStatus.BAD_REQUEST;
            case WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_WALLET_OWNER -> HttpStatus.FORBIDDEN;
            case INSUFFICIENT_BALANCE, OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static <T> ResponseEntity<?> toResponse(Result<T> result, Function<T, ResponseEntity<?>> onSuccess) {
        if (result.isSuccess()) {
            return onSuccess.apply(result.value());
        }
        RecognitionIncentivesError error = (RecognitionIncentivesError) result.error();
        Map<String, Object> details = result.details() == null ? Map.of() : result.details();
        return ProblemDetails.of(toStatusFromError(error), error, result.message(), details);
    }

    public static ResponseEntity<?> toError(RecognitionIncentivesError error, String localizedMessage) {
        return ProblemDetails.of(toStatusFromError(error), error, localizedMessage);
    }
}
