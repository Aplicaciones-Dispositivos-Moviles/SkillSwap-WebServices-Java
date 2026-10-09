package com.innovify.skillswap.reputation.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.interfaces.rest.transform.ReputationActionResultAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ReputationActionResultAssemblerTest {

    @ParameterizedTest
    @CsvSource({
            "NONE,400", "REPUTATION_NOT_FOUND,404", "NOT_REPUTATION_OWNER,403", "OPERATION_CANCELLED,409",
            "DATABASE_ERROR,500", "INTERNAL_SERVER_ERROR,500"
    })
    void everyErrorMapsToItsStatus(ReputationError error, int status) {
        assertThat(ReputationActionResultAssembler.toStatusFromError(error).value()).isEqualTo(status);
    }

    @Test
    void toError_buildsAProblemDetailWithTheErrorAsTitle() {
        ResponseEntity<?> response = ReputationActionResultAssembler.toError(
                ReputationError.NOT_REPUTATION_OWNER, "No es tuya");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("NotReputationOwner");
        assertThat(problem.getDetail()).isEqualTo("No es tuya");
    }
}
