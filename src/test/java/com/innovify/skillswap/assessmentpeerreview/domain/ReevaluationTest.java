package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReevaluationTest {

    private static final int STUDENT_ID = 7;
    private static final int SENIOR_ID = 9;

    @Test
    void constructor_keepsTheSourceTheSeniorAndTheTrimmedJustification() {
        Instant before = Instant.now();

        Reevaluation reevaluation = new Reevaluation(ReevaluationSourceType.CASE, 4, STUDENT_ID, SENIOR_ID,
                "  The rubric was not applied.  ");

        assertThat(reevaluation.getSourceType()).isEqualTo(ReevaluationSourceType.CASE);
        assertThat(reevaluation.getSourceId()).isEqualTo(4);
        assertThat(reevaluation.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(reevaluation.getOrderedByUserId()).isEqualTo(SENIOR_ID);
        assertThat(reevaluation.getJustification()).isEqualTo("The rubric was not applied.");
        assertThat(reevaluation.getCreatedAt()).isBetween(before, Instant.now());
    }

    @ParameterizedTest
    @CsvSource({"0,7,9", "4,0,9", "4,7,0"})
    void constructor_withAnInvalidId_throwsDomainException(int sourceId, int studentId, int orderedByUserId) {
        assertThatThrownBy(() -> new Reevaluation(ReevaluationSourceType.ATTEMPT, sourceId, studentId, orderedByUserId,
                "Doubts.")).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withoutSourceType_throwsDomainException() {
        assertThatThrownBy(() -> new Reevaluation(null, 4, STUDENT_ID, SENIOR_ID, "Doubts."))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_whenTheVerifierIsTheStudent_throwsDomainException() {
        assertThatThrownBy(() -> new Reevaluation(ReevaluationSourceType.ATTEMPT, 4, STUDENT_ID, STUDENT_ID, "Doubts."))
                .isInstanceOf(DomainException.class).hasMessageContaining("own result");
    }

    @Test
    void constructor_withAnEmptyOrTooLongJustification_throwsDomainException() {
        assertThatThrownBy(() -> new Reevaluation(ReevaluationSourceType.ATTEMPT, 4, STUDENT_ID, SENIOR_ID, "   "))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Reevaluation(ReevaluationSourceType.ATTEMPT, 4, STUDENT_ID, SENIOR_ID, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Reevaluation(ReevaluationSourceType.ATTEMPT, 4, STUDENT_ID, SENIOR_ID,
                "j".repeat(Reevaluation.MAX_JUSTIFICATION_LENGTH + 1))).isInstanceOf(DomainException.class);
    }

    @Test
    void sourceType_usesTheStoredValues() {
        assertThat(ReevaluationSourceType.ATTEMPT.value()).isEqualTo("Attempt");
        assertThat(ReevaluationSourceType.CASE.value()).isEqualTo("Case");
        assertThat(ReevaluationSourceType.fromValue("case")).isEqualTo(ReevaluationSourceType.CASE);
        assertThatThrownBy(() -> ReevaluationSourceType.fromValue("Other"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
