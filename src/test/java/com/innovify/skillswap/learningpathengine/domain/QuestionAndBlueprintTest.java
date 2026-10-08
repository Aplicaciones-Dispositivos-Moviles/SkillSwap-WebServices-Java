package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestionAndBlueprintTest {

    // ---------- Question ----------

    @Test
    void question_trimsTheTextAndTheAnswers() {
        Question question = new Question("  What is 200?  ", List.of(" OK ", "Created", "Not Found", "Teapot"), 0);

        assertThat(question.getQuestionString()).isEqualTo("What is 200?");
        assertThat(question.getAnswers().get(0)).isEqualTo("OK");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5})
    void question_withADifferentNumberOfAnswers_throws(int count) {
        List<String> answers = IntStream.rangeClosed(1, count).mapToObj(i -> "answer " + i).toList();

        assertThatThrownBy(() -> new Question("Question?", answers, 0)).isInstanceOf(DomainException.class);
    }

    @Test
    void question_withBlankOrRepeatedAnswers_throws() {
        assertThatThrownBy(() -> new Question("Question?", List.of("a", "b", "c", " "), 0))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Question("Question?", List.of("a", "b", "c", "A"), 0))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 4})
    void question_withACorrectAnswerOutOfRange_throws(int index) {
        assertThatThrownBy(() -> new Question("Question?", List.of("a", "b", "c", "d"), index))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void question_withBlankText_throws() {
        assertThatThrownBy(() -> new Question("  ", List.of("a", "b", "c", "d"), 0))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void question_withTextOrAnswerOverTheLimit_throws() {
        assertThatThrownBy(() -> new Question("q".repeat(Question.MAX_QUESTION_LENGTH + 1),
                List.of("a", "b", "c", "d"), 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Question("Question?",
                List.of("a".repeat(Question.MAX_ANSWER_LENGTH + 1), "b", "c", "d"), 0))
                .isInstanceOf(DomainException.class);
    }

    // ---------- AssessmentBlueprint ----------

    @Test
    void blueprint_withTheRightNumberOfQuestions_isCreated() {
        AssessmentBlueprint blueprint = new AssessmentBlueprint(7, " rest-api-design ", TestData.questions(5));

        assertThat(blueprint.getPathNodeId()).isEqualTo(7);
        assertThat(blueprint.getSkillTag()).isEqualTo("rest-api-design");
        assertThat(blueprint.getQuestions()).hasSize(AssessmentBlueprint.QUESTION_COUNT);
        assertThat(blueprint.getGeneratedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 6})
    void blueprint_withAWrongNumberOfQuestions_throws(int count) {
        assertThatThrownBy(() -> new AssessmentBlueprint(7, "rest-api-design", TestData.questions(count)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void blueprint_withRepeatedQuestions_throws() {
        List<Question> questions = new ArrayList<>(TestData.questions(4));
        questions.add(new Question("QUESTION 1?", List.of("w", "x", "y", "z"), 0));

        assertThatThrownBy(() -> new AssessmentBlueprint(7, "rest-api-design", questions))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void blueprint_withInvalidNodeOrSkill_throws() {
        assertThatThrownBy(() -> new AssessmentBlueprint(0, "rest-api-design", TestData.questions(5)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new AssessmentBlueprint(7, " ", TestData.questions(5)))
                .isInstanceOf(DomainException.class);
    }
}
