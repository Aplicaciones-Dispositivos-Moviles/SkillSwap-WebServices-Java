package com.innovify.skillswap.assessmentpeerreview.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintQuestionView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The correct answers of the questions are 1, 2, 3, 0, 1. */
class WeakSubTopicResolverTest {

    private static final List<Integer> CORRECT = List.of(1, 2, 3, 0, 1);

    private static List<BlueprintQuestionView> questions(List<String> subTopics) {
        return java.util.stream.IntStream.range(0, CORRECT.size())
                .mapToObj(index -> new BlueprintQuestionView("Question " + (index + 1) + "?",
                        List.of("a", "b", "c", "d"), CORRECT.get(index), subTopics.get(index)))
                .toList();
    }

    private static AssessmentAttempt attempt(List<Integer> answers) {
        return new AssessmentAttempt(1, 1, answers, CORRECT);
    }

    @Test
    void resolve_returnsTheSubTopicWhereTheStudentFailedTheMost() {
        var questions = questions(List.of("Joins", "Indexes", "Indexes", "Joins", "Indexes"));

        // Wrong: questions 2, 3 and 4 (Indexes, Indexes, Joins).
        String weak = WeakSubTopicResolver.resolve(attempt(List.of(1, 0, 0, 1, 1)), questions);

        assertThat(weak).isEqualTo("Indexes");
    }

    @Test
    void resolve_inAPassedAttemptWithOneError_returnsTheSubTopicOfThatError() {
        var questions = questions(List.of("Joins", "Indexes", "Indexes", "Joins", "Keys"));

        String weak = WeakSubTopicResolver.resolve(attempt(List.of(1, 2, 3, 0, 3)), questions);

        assertThat(weak).isEqualTo("Keys");
    }

    @Test
    void resolve_withEveryAnswerCorrect_returnsNull() {
        var questions = questions(List.of("Joins", "Indexes", "Indexes", "Joins", "Keys"));

        assertThat(WeakSubTopicResolver.resolve(attempt(CORRECT), questions)).isNull();
    }

    @Test
    void resolve_whenTheQuestionsHaveNoSubTopic_returnsNull() {
        var questions = questions(java.util.Arrays.asList(null, null, null, null, null));

        assertThat(WeakSubTopicResolver.resolve(attempt(List.of(0, 0, 0, 1, 0)), questions)).isNull();
    }
}
