package com.innovify.skillswap.moderationdisputes.interfaces.rest.transform;

import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps application results and domain errors to HTTP responses. */
public final class ModerationDisputesActionResultAssembler {

    private ModerationDisputesActionResultAssembler() {
    }

    public static HttpStatus toStatusFromError(ModerationDisputesError error) {
        return switch (error) {
            case NONE, INVALID_OUTCOME, INVALID_DISPUTE_STATUS, COORDINATOR_NOTES_REQUIRED,
                 COORDINATOR_NOTES_TOO_LONG -> HttpStatus.BAD_REQUEST;
            case DISPUTE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_A_VERIFIER, NOT_ASSIGNED_REVIEWER -> HttpStatus.FORBIDDEN;
            case DISPUTE_ALREADY_RESOLVED, CERTIFICATE_NOT_SUSPICIOUS, OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static <T> ResponseEntity<?> toResponse(Result<T> result, Function<T, ResponseEntity<?>> onSuccess) {
        if (result.isSuccess()) {
            return onSuccess.apply(result.value());
        }
        ModerationDisputesError error = (ModerationDisputesError) result.error();
        Map<String, Object> details = result.details() == null ? Map.of() : result.details();
        return ProblemDetails.of(toStatusFromError(error), error, result.message(), details);
    }

    public static ResponseEntity<?> toError(ModerationDisputesError error, String localizedMessage) {
        return ProblemDetails.of(toStatusFromError(error), error, localizedMessage);
    }
}
