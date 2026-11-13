package com.innovify.skillswap.shared.interfaces.rest.problemdetails;

import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Builds the error responses of the API: a {@link ProblemDetail} whose {@code title} is the PascalCase error
 * code (e.g. "UsernameAlreadyTaken") and whose {@code detail} is the localized message.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ResponseEntity<ProblemDetail> of(HttpStatus status, Enum<?> error, String message) {
        return of(status, error, message, Map.of());
    }

    /** The extensions are added as extra members of the response. */
    public static ResponseEntity<ProblemDetail> of(HttpStatus status, Enum<?> error, String message,
                                                    Map<String, Object> extensions) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(error == null ? "Error" : ErrorCodes.of(error));
        extensions.forEach(problem::setProperty);
        return ResponseEntity.status(status).body(problem);
    }
}
