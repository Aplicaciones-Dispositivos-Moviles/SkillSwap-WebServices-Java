package com.innovify.skillswap.credentialverification.interfaces.rest.transform;

import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps application results and domain errors to HTTP responses. */
public final class CredentialVerificationActionResultAssembler {

    private CredentialVerificationActionResultAssembler() {
    }

    public static HttpStatus toStatusFromError(CredentialVerificationError error) {
        return switch (error) {
            case FILE_REQUIRED, FIELD_TOO_LONG, NONE -> HttpStatus.BAD_REQUEST;
            case INVALID_FILE_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case DUPLICATE_FILE, INVALID_STATUS_TRANSITION, OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case CERTIFICATE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_CERTIFICATE_OWNER -> HttpStatus.FORBIDDEN;
            case STORAGE_ERROR -> HttpStatus.BAD_GATEWAY;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static <T> ResponseEntity<?> toResponse(Result<T> result, Function<T, ResponseEntity<?>> onSuccess) {
        if (result.isSuccess()) {
            return onSuccess.apply(result.value());
        }
        CredentialVerificationError error = (CredentialVerificationError) result.error();
        Map<String, Object> details = result.details() == null ? Map.of() : result.details();
        return ProblemDetails.of(toStatusFromError(error), error, result.message(), details);
    }

    public static ResponseEntity<?> toError(CredentialVerificationError error, String localizedMessage) {
        return ProblemDetails.of(toStatusFromError(error), error, localizedMessage);
    }
}
