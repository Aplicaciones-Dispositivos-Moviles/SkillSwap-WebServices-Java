package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesActionResultAssembler;
import com.innovify.skillswap.shared.application.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class RecognitionIncentivesActionResultAssemblerTest {

    @ParameterizedTest
    @CsvSource({
            "NONE,400", "INVALID_REDEMPTION_ITEM,400", "WALLET_NOT_FOUND,404", "NOT_WALLET_OWNER,403",
            "INSUFFICIENT_BALANCE,409", "OPERATION_CANCELLED,409", "DATABASE_ERROR,500",
            "INTERNAL_SERVER_ERROR,500"
    })
    void everyErrorMapsToItsStatus(RecognitionIncentivesError error, int status) {
        assertThat(RecognitionIncentivesActionResultAssembler.toStatusFromError(error).value()).isEqualTo(status);
    }

    @Test
    void toError_buildsAProblemDetailWithTheErrorAsTitle() {
        ResponseEntity<?> response = RecognitionIncentivesActionResultAssembler.toError(
                RecognitionIncentivesError.NOT_WALLET_OWNER, "No es tuya");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("NotWalletOwner");
        assertThat(problem.getDetail()).isEqualTo("No es tuya");
    }

    @Test
    void toResponse_onSuccess_appliesTheSuccessMapping() {
        ResponseEntity<?> response = RecognitionIncentivesActionResultAssembler.toResponse(
                Result.success("ok"), value -> ResponseEntity.ok(value));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("ok");
    }

    @Test
    void toResponse_onFailure_buildsTheProblemDetail() {
        Result<String> failure = Result.failure(RecognitionIncentivesError.INSUFFICIENT_BALANCE, "Sin saldo");

        ResponseEntity<?> response = RecognitionIncentivesActionResultAssembler.toResponse(
                failure, value -> ResponseEntity.ok(value));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(((ProblemDetail) response.getBody()).getTitle()).isEqualTo("InsufficientBalance");
    }
}
