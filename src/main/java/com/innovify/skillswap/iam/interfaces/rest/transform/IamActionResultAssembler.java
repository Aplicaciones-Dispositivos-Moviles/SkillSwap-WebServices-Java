package com.innovify.skillswap.iam.interfaces.rest.transform;

import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps application results and domain errors to HTTP responses. */
public final class IamActionResultAssembler {

    private IamActionResultAssembler() {
    }

    static HttpStatus toStatusFromIamError(IamError error) {
        return switch (error) {
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case USER_BANNED, NOT_PROFILE_OWNER, EMAIL_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
            case USERNAME_ALREADY_TAKEN, EMAIL_ALREADY_TAKEN, OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case INVALID_INSTITUTIONAL_EMAIL, INVALID_USERNAME, WEAK_PASSWORD, BIO_TOO_LONG,
                 INVALID_VERIFICATION_TOKEN, NONE -> HttpStatus.BAD_REQUEST;
            case VERIFICATION_TOKEN_EXPIRED -> HttpStatus.GONE;
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static <T> ResponseEntity<?> toResponse(Result<T> result, Function<T, ResponseEntity<?>> onSuccess) {
        if (result.isSuccess()) {
            return onSuccess.apply(result.value());
        }
        IamError error = (IamError) result.error();
        return ProblemDetails.of(toStatusFromIamError(error), error, result.message());
    }

    public static ResponseEntity<?> toUserNotFound(String localizedMessage) {
        return ProblemDetails.of(toStatusFromIamError(IamError.USER_NOT_FOUND), IamError.USER_NOT_FOUND,
                localizedMessage);
    }
}
