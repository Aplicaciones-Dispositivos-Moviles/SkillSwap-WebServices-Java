package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** US19 escenario 3: a delivery is approved by the AI only with the minimum score and a confidence that is not low. */
class PreliminaryEvaluationTest {

    private static final int PASSING_SCORE = 70;

    @ParameterizedTest(name = "score {0}, confidence {1} -> sufficient {2}")
    @CsvSource({
            "70, 70, true",
            "100, 100, true",
            "69, 100, false",
            "100, 69, false",
            "0, 0, false"})
    void isSufficientFor_needsTheMinimumScoreAndEnoughConfidence(int score, int confidence, boolean sufficient) {
        assertThat(new PreliminaryEvaluation(score, confidence, "").isSufficientFor(PASSING_SCORE))
                .isEqualTo(sufficient);
    }

    @Test
    void hasLowConfidence_belowTheThreshold() {
        assertThat(new PreliminaryEvaluation(90, PreliminaryEvaluation.MIN_CONFIDENCE - 1, "").hasLowConfidence())
                .isTrue();
        assertThat(new PreliminaryEvaluation(90, PreliminaryEvaluation.MIN_CONFIDENCE, "").hasLowConfidence())
                .isFalse();
    }

    @Test
    void create_stripsTheFeedbackAndAcceptsNone() {
        assertThat(new PreliminaryEvaluation(80, 90, "  Good README.  ").feedback()).isEqualTo("Good README.");
        assertThat(new PreliminaryEvaluation(80, 90, null).feedback()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"-1, 50", "101, 50", "50, -1", "50, 101"})
    void create_withAScoreOrConfidenceOutOfRange_isRejected(int score, int confidence) {
        assertThatThrownBy(() -> new PreliminaryEvaluation(score, confidence, ""))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void create_withAFeedbackThatIsTooLong_isRejected() {
        String feedback = "a".repeat(PreliminaryEvaluation.MAX_FEEDBACK_LENGTH + 1);

        assertThatThrownBy(() -> new PreliminaryEvaluation(80, 90, feedback)).isInstanceOf(DomainException.class)
                .hasMessageContaining("1000");
    }
}
