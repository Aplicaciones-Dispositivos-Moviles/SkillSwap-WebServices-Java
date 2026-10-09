package com.innovify.skillswap.subscriptionbilling.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform.SubscriptionBillingActionResultAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class SubscriptionBillingActionResultAssemblerTest {

    @ParameterizedTest
    @CsvSource({
            "NONE,400", "INVALID_PRODUCT,400", "INVALID_WEBHOOK_EVENT,400", "INVALID_WEBHOOK_AUTHORIZATION,401",
            "PURCHASE_NOT_VERIFIED,422", "SUBSCRIPTION_NOT_FOUND,404", "NOT_SUBSCRIPTION_OWNER,403",
            "SUBSCRIPTION_NOT_ACTIVE,409", "PAYMENT_GATEWAY_UNAVAILABLE,503", "OPERATION_CANCELLED,409",
            "DATABASE_ERROR,500", "INTERNAL_SERVER_ERROR,500"
    })
    void everyErrorMapsToItsStatus(SubscriptionBillingError error, int status) {
        assertThat(SubscriptionBillingActionResultAssembler.toStatusFromError(error).value()).isEqualTo(status);
    }

    @Test
    void toResponse_onFailure_buildsAProblemDetailWithTheErrorAsTitle() {
        ResponseEntity<?> response = SubscriptionBillingActionResultAssembler.toResponse(
                Result.failure(SubscriptionBillingError.PURCHASE_NOT_VERIFIED, "No hay compra"),
                value -> ResponseEntity.ok(value));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("PurchaseNotVerified");
        assertThat(problem.getDetail()).isEqualTo("No hay compra");
    }

    @Test
    void toResponse_onSuccess_appliesTheSuccessMapping() {
        ResponseEntity<?> response = SubscriptionBillingActionResultAssembler.toResponse(Result.success("ok"),
                value -> ResponseEntity.ok(value));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("ok");
    }
}
