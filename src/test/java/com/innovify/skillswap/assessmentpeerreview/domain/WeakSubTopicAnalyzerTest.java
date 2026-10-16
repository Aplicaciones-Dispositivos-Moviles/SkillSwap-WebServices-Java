package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.services.QuestionOutcome;
import com.innovify.skillswap.assessmentpeerreview.domain.services.WeakSubTopicAnalyzer;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeakSubTopicAnalyzerTest {

    private static QuestionOutcome right(String subTopic) {
        return new QuestionOutcome(subTopic, true);
    }

    private static QuestionOutcome wrong(String subTopic) {
        return new QuestionOutcome(subTopic, false);
    }

    @Test
    void find_returnsTheSubTopicWithMostWrongAnswers() {
        List<QuestionOutcome> outcomes = List.of(wrong("Joins"), right("Indexes"), wrong("Indexes"),
                wrong("Indexes"), right("Joins"));

        assertThat(WeakSubTopicAnalyzer.find(outcomes)).contains("Indexes");
    }

    @Test
    void find_onATie_returnsTheSubTopicOfTheFirstQuestion() {
        List<QuestionOutcome> outcomes = List.of(right("Joins"), wrong("Indexes"), wrong("Joins"), right("Indexes"));

        assertThat(WeakSubTopicAnalyzer.find(outcomes)).contains("Indexes");
    }

    @Test
    void find_withNoWrongAnswers_returnsEmpty() {
        List<QuestionOutcome> outcomes = List.of(right("Joins"), right("Indexes"), right("Joins"));

        assertThat(WeakSubTopicAnalyzer.find(outcomes)).isEmpty();
    }

    @Test
    void find_ignoresQuestionsWithoutSubTopic() {
        List<QuestionOutcome> outcomes = List.of(wrong(null), wrong(null), wrong("Joins"), right("Indexes"));

        assertThat(WeakSubTopicAnalyzer.find(outcomes)).contains("Joins");
    }

    @Test
    void find_whenNoQuestionHasSubTopic_returnsEmpty() {
        assertThat(WeakSubTopicAnalyzer.find(List.of(wrong(null), wrong(null)))).isEmpty();
    }

    @Test
    void find_withNoQuestions_returnsEmpty() {
        assertThat(WeakSubTopicAnalyzer.find(List.of())).isEmpty();
        assertThat(WeakSubTopicAnalyzer.find(null)).isEmpty();
    }
}
