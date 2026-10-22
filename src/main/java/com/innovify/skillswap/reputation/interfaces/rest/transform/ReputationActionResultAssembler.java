package com.innovify.skillswap.reputation.interfaces.rest.transform;

import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps Reputation errors to HTTP responses. */
public final class ReputationActionResultAssembler {

    private ReputationActionResultAssembler() {
    }

    public static HttpStatus toStatusFromError(ReputationError error) {
        return switch (error) {
            case NONE, INVALID_PERIOD, INVALID_LIMIT, INVALID_ORDER -> HttpStatus.BAD_REQUEST;
            case REPUTATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_REPUTATION_OWNER, NOT_SENIOR_VERIFIER -> HttpStatus.FORBIDDEN;
            case OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static ResponseEntity<?> toError(ReputationError error, String localizedMessage) {
        return ProblemDetails.of(toStatusFromError(error), error, localizedMessage);
    }
}
