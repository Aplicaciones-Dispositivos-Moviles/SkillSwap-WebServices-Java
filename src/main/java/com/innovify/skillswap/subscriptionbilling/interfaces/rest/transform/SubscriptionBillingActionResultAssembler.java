package com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform;

import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps application results and domain errors to HTTP responses. */
public final class SubscriptionBillingActionResultAssembler {

    private SubscriptionBillingActionResultAssembler() {
    }

    public static HttpStatus toStatusFromError(SubscriptionBillingError error) {
        return switch (error) {
            case NONE, INVALID_PRODUCT, INVALID_WEBHOOK_EVENT -> HttpStatus.BAD_REQUEST;
            case INVALID_WEBHOOK_AUTHORIZATION -> HttpStatus.UNAUTHORIZED;
            case NOT_SUBSCRIPTION_OWNER -> HttpStatus.FORBIDDEN;
            case SUBSCRIPTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SUBSCRIPTION_NOT_ACTIVE, OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case PURCHASE_NOT_VERIFIED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case PAYMENT_GATEWAY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static <T> ResponseEntity<?> toResponse(Result<T> result, Function<T, ResponseEntity<?>> onSuccess) {
        if (result.isSuccess()) {
            return onSuccess.apply(result.value());
        }
        SubscriptionBillingError error = (SubscriptionBillingError) result.error();
        Map<String, Object> details = result.details() == null ? Map.of() : result.details();
        return ProblemDetails.of(toStatusFromError(error), error, result.message(), details);
    }

    public static ResponseEntity<?> toError(SubscriptionBillingError error, String localizedMessage) {
        return ProblemDetails.of(toStatusFromError(error), error, localizedMessage);
    }
}
