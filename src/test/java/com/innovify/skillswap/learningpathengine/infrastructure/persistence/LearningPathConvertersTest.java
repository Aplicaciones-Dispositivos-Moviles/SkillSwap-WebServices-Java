package com.innovify.skillswap.learningpathengine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters.CareerGoalConverter;
import com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters.NodeStatusConverter;
import com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters.PathStatusConverter;
import com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.converters.QuestionListConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LearningPathConvertersTest {

    private final NodeStatusConverter nodeStatusConverter = new NodeStatusConverter();
    private final PathStatusConverter pathStatusConverter = new PathStatusConverter();
    private final CareerGoalConverter goalConverter = new CareerGoalConverter();
    private final QuestionListConverter questionsConverter = new QuestionListConverter();

    @ParameterizedTest
    @EnumSource(NodeStatus.class)
    void nodeStatus_roundTripsAsThePascalCaseText(NodeStatus status) {
        String text = nodeStatusConverter.convertToDatabaseColumn(status);

        assertThat(text).isEqualTo(status.value());
        assertThat(nodeStatusConverter.convertToEntityAttribute(text)).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(PathStatus.class)
    void pathStatus_roundTripsAsThePascalCaseText(PathStatus status) {
        String text = pathStatusConverter.convertToDatabaseColumn(status);

        assertThat(text).isEqualTo(status.value());
        assertThat(pathStatusConverter.convertToEntityAttribute(text)).isEqualTo(status);
    }

    @Test
    void statuses_keepNullsAsNulls() {
        assertThat(nodeStatusConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(nodeStatusConverter.convertToEntityAttribute(null)).isNull();
        assertThat(pathStatusConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(pathStatusConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void careerGoal_isStoredWithTheJsonShapeOfTheCSharpApi() {
        CareerGoal goal = new CareerGoal("I want to build APIs", List.of("rest-api-design", "authentication-jwt"));

        assertThat(goalConverter.convertToDatabaseColumn(goal)).isEqualTo(
                "{\"rawText\":\"I want to build APIs\",\"skillTags\":[\"rest-api-design\",\"authentication-jwt\"]}");
    }

    @Test
    void careerGoal_roundTripsTextWithQuotesAndAccents() {
        CareerGoal goal = new CareerGoal("quiero \"aprender\" autenticación", List.of("authentication-jwt"));

        CareerGoal loaded = goalConverter.convertToEntityAttribute(goalConverter.convertToDatabaseColumn(goal));

        assertThat(loaded).isEqualTo(goal);
    }

    @Test
    void careerGoal_readsWhatTheCSharpApiWrote() {
        CareerGoal loaded = goalConverter
                .convertToEntityAttribute("{\"rawText\": \"Quiero SQL\", \"skillTags\": [\"sql-fundamentals\"]}");

        assertThat(loaded.rawText()).isEqualTo("Quiero SQL");
        assertThat(loaded.mappedSkillTags()).containsExactly("sql-fundamentals");
    }

    @Test
    void careerGoal_keepsNullsAsNulls() {
        assertThat(goalConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(goalConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void questions_areStoredWithTheJsonShapeOfTheCSharpApi() {
        List<Question> questions = List.of(new Question("What is HTTP?", List.of("a", "b", "c", "d"), 2));

        assertThat(questionsConverter.convertToDatabaseColumn(questions)).isEqualTo(
                "[{\"question\":\"What is HTTP?\",\"answers\":[\"a\",\"b\",\"c\",\"d\"],\"correctAnswer\":2}]");
    }

    @Test
    void questions_roundTripInOrderWithTheirCorrectAnswers() {
        List<Question> questions = TestData.questions(5);

        List<Question> loaded = questionsConverter
                .convertToEntityAttribute(questionsConverter.convertToDatabaseColumn(questions));

        assertThat(loaded).isEqualTo(questions);
    }

    @Test
    void questions_keepNullsAsNulls() {
        assertThat(questionsConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(questionsConverter.convertToEntityAttribute(null)).isNull();
    }
}
