package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AssessmentAttemptTest {

    private static final List<Integer> CORRECT = List.of(1, 2, 3, 0, 1);

    private static AssessmentAttempt attempt(List<Integer> selected) {
        return new AssessmentAttempt(1, 1, selected, CORRECT);
    }

    @Test
    void constructor_withAllAnswersCorrect_passesWithFullScore() {
        AssessmentAttempt attempt = attempt(List.of(1, 2, 3, 0, 1));

        assertThat(attempt.getScore().value()).isEqualTo(5);
        assertThat(attempt.getScore().total()).isEqualTo(5);
        assertThat(attempt.isPassed()).isTrue();
        assertThat(attempt.getSelectedAnswers()).containsExactly(1, 2, 3, 0, 1);
    }

    @Test
    void constructor_withFourCorrectAnswers_passes() {
        AssessmentAttempt attempt = attempt(List.of(1, 2, 3, 0, 3));

        assertThat(attempt.getScore().value()).isEqualTo(4);
        assertThat(attempt.isPassed()).isTrue();
    }

    @Test
    void constructor_withThreeCorrectAnswers_doesNotPass() {
        AssessmentAttempt attempt = attempt(List.of(1, 2, 3, 3, 3));

        assertThat(attempt.getScore().value()).isEqualTo(3);
        assertThat(attempt.isPassed()).isFalse();
    }

    @Test
    void constructor_withNoCorrectAnswers_scoresZero() {
        AssessmentAttempt attempt = attempt(List.of(0, 0, 0, 1, 0));

        assertThat(attempt.getScore().value()).isZero();
        assertThat(attempt.isPassed()).isFalse();
    }

    @Test
    void constructor_setsTheOwnerBlueprintAndCompletionTime() {
        Instant before = Instant.now();

        AssessmentAttempt attempt = new AssessmentAttempt(9, 4, List.of(1, 2, 3, 0, 1), CORRECT);

        assertThat(attempt.getBlueprintId()).isEqualTo(9);
        assertThat(attempt.getStudentId()).isEqualTo(4);
        assertThat(attempt.getCompletedAt()).isBetween(before, Instant.now());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 4})
    void constructor_withAnAnswerOutOfRange_throwsDomainException(int answer) {
        assertThatThrownBy(() -> attempt(List.of(1, 2, 3, 0, answer))).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withANullAnswer_throwsDomainException() {
        assertThatThrownBy(() -> attempt(Arrays.asList(1, 2, 3, 0, null))).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withAWrongNumberOfAnswers_throwsDomainException() {
        assertThatThrownBy(() -> attempt(List.of(1, 2, 3, 0))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> attempt(List.of(1, 2, 3, 0, 1, 1))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> attempt(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withTooFewQuestions_throwsDomainException() {
        assertThatThrownBy(() -> new AssessmentAttempt(1, 1, List.of(1, 2, 3), List.of(1, 2, 3)))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,0"})
    void constructor_withAnInvalidId_throwsDomainException(int blueprintId, int studentId) {
        assertThatThrownBy(() -> new AssessmentAttempt(blueprintId, studentId, List.of(1, 2, 3, 0, 1), CORRECT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void incorrectQuestionIndexes_listsTheWrongAnswers() {
        AssessmentAttempt attempt = attempt(List.of(1, 0, 3, 0, 2));

        assertThat(attempt.incorrectQuestionIndexes(CORRECT)).containsExactly(1, 4);
    }

    @Test
    void incorrectQuestionIndexes_whenEverythingIsCorrect_isEmpty() {
        assertThat(attempt(List.of(1, 2, 3, 0, 1)).incorrectQuestionIndexes(CORRECT)).isEmpty();
    }

    @Test
    void incorrectQuestionIndexes_withAnotherNumberOfAnswers_throwsDomainException() {
        AssessmentAttempt attempt = attempt(List.of(1, 2, 3, 0, 1));

        assertThatThrownBy(() -> attempt.incorrectQuestionIndexes(List.of(1, 2, 3)))
                .isInstanceOf(DomainException.class);
    }
}
