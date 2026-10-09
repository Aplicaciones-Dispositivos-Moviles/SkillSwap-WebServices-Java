package com.innovify.skillswap.learningpathengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathActionResultAssembler;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class LearningPathActionResultAssemblerTest {

    @ParameterizedTest
    @CsvSource({
            "INVALID_GOAL,400", "GOAL_NOT_INTERPRETABLE,422", "GOAL_ALREADY_ACHIEVED,409",
            "PLAN_LIMIT_REACHED,409", "PATH_NOT_ACTIVE,409", "PATH_NOT_PAUSED,409", "PATH_PAUSED,409",
            "PATH_NOT_FOUND,404", "NOT_PATH_OWNER,403", "NODE_NOT_FOUND,404",
            "NODE_LOCKED,409", "NODE_ALREADY_COMPLETED,409", "QUESTION_GENERATION_FAILED,503",
            "OPERATION_CANCELLED,409", "DATABASE_ERROR,500", "INTERNAL_SERVER_ERROR,500", "NONE,400"
    })
    void everyErrorMapsToItsStatus(LearningPathError error, int status) {
        assertThat(LearningPathActionResultAssembler.toStatusFromError(error).value()).isEqualTo(status);
    }

    @Test
    void success_runsTheSuccessAction() {
        ResponseEntity<?> response = LearningPathActionResultAssembler.toResponse(Result.success("ok"),
                value -> ResponseEntity.status(HttpStatus.CREATED).body(value));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("ok");
    }

    @Test
    void failure_becomesAProblemDetailWithTheCodeAsTitleAndTheDetailsAsExtensions() {
        Result<String> failure = Result.failure(LearningPathError.NODE_LOCKED, "Locked.",
                Map.of("pendingPrerequisites", List.of("http-basics")));

        ResponseEntity<?> response = LearningPathActionResultAssembler.toResponse(failure, value -> null);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo("NodeLocked");
        assertThat(problem.getDetail()).isEqualTo("Locked.");
        assertThat(problem.getProperties()).containsEntry("pendingPrerequisites", List.of("http-basics"));
    }

    @Test
    void failure_withoutDetails_hasNoExtensions() {
        Result<String> failure = Result.failure(LearningPathError.PATH_NOT_FOUND, "Missing.");

        ProblemDetail problem = (ProblemDetail) LearningPathActionResultAssembler
                .toResponse(failure, value -> null).getBody();

        assertThat(problem.getTitle()).isEqualTo("PathNotFound");
        assertThat(problem.getProperties()).isNullOrEmpty();
    }
}
