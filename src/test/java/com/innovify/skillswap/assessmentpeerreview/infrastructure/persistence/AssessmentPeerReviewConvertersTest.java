package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.Score;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters.CaseStatusConverter;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters.CaseTypeConverter;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters.IntegerListConverter;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters.ReviewDecisionConverter;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters.ScoreConverter;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters.StringListConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AssessmentPeerReviewConvertersTest {

    private final IntegerListConverter integers = new IntegerListConverter();
    private final StringListConverter strings = new StringListConverter();
    private final ScoreConverter scores = new ScoreConverter();
    private final CaseStatusConverter statuses = new CaseStatusConverter();
    private final CaseTypeConverter types = new CaseTypeConverter();
    private final ReviewDecisionConverter decisions = new ReviewDecisionConverter();

    @Test
    void integerList_isStoredAsAJsonArray() {
        assertThat(integers.convertToDatabaseColumn(List.of(1, 2, 3, 0, 3))).isEqualTo("[1,2,3,0,3]");
    }

    @Test
    void integerList_readsWhatPostgresReturnsForJsonb() {
        // jsonb normalizes the text with a space after each comma.
        assertThat(integers.convertToEntityAttribute("[1, 2, 3, 0, 3]")).containsExactly(1, 2, 3, 0, 3);
    }

    @Test
    void integerList_roundTrips() {
        List<Integer> answers = List.of(0, 0, 0, 1, 0);

        assertThat(integers.convertToEntityAttribute(integers.convertToDatabaseColumn(answers))).isEqualTo(answers);
    }

    @Test
    void stringList_isStoredAsAJsonArray() {
        assertThat(strings.convertToDatabaseColumn(List.of("http-basics", "rest-api-design")))
                .isEqualTo("[\"http-basics\",\"rest-api-design\"]");
    }

    @Test
    void stringList_readsWhatPostgresReturnsForJsonb() {
        assertThat(strings.convertToEntityAttribute("[\"http-basics\", \"rest-api-design\"]"))
                .containsExactly("http-basics", "rest-api-design");
    }

    @Test
    void lists_keepNullsAsNulls() {
        assertThat(integers.convertToDatabaseColumn(null)).isNull();
        assertThat(integers.convertToEntityAttribute(null)).isNull();
        assertThat(strings.convertToDatabaseColumn(null)).isNull();
        assertThat(strings.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void score_isStoredAsValueOverTotal() {
        assertThat(scores.convertToDatabaseColumn(new Score(4, 5))).isEqualTo("4/5");
        assertThat(scores.convertToEntityAttribute("4/5")).isEqualTo(new Score(4, 5));
    }

    @Test
    void score_withAnUnknownShape_isRejected() {
        assertThatThrownBy(() -> scores.convertToEntityAttribute("four")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(CaseStatus.class)
    void caseStatus_roundTripsAsThePascalCaseText(CaseStatus status) {
        String text = statuses.convertToDatabaseColumn(status);

        assertThat(text).isEqualTo(status.value());
        assertThat(statuses.convertToEntityAttribute(text)).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(CaseType.class)
    void caseType_roundTripsAsThePascalCaseText(CaseType type) {
        String text = types.convertToDatabaseColumn(type);

        assertThat(text).isEqualTo(type.value());
        assertThat(types.convertToEntityAttribute(text)).isEqualTo(type);
    }

    @Test
    void caseType_isStoredAsQuizOrMiniProject() {
        assertThat(types.convertToDatabaseColumn(CaseType.QUIZ)).isEqualTo("Quiz");
        assertThat(types.convertToDatabaseColumn(CaseType.MINI_PROJECT)).isEqualTo("MiniProject");
    }

    @Test
    void caseType_withAnUnknownText_isRejected() {
        assertThatThrownBy(() -> types.convertToEntityAttribute("Essay")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(ReviewDecision.class)
    void reviewDecision_roundTripsAsThePascalCaseText(ReviewDecision decision) {
        String text = decisions.convertToDatabaseColumn(decision);

        assertThat(text).isEqualTo(decision.value());
        assertThat(decisions.convertToEntityAttribute(text)).isEqualTo(decision);
    }

    @Test
    void scalars_keepNullsAsNulls() {
        assertThat(scores.convertToDatabaseColumn(null)).isNull();
        assertThat(scores.convertToEntityAttribute(null)).isNull();
        assertThat(statuses.convertToDatabaseColumn(null)).isNull();
        assertThat(statuses.convertToEntityAttribute(null)).isNull();
        assertThat(types.convertToDatabaseColumn(null)).isNull();
        assertThat(types.convertToEntityAttribute(null)).isNull();
        assertThat(decisions.convertToDatabaseColumn(null)).isNull();
        assertThat(decisions.convertToEntityAttribute(null)).isNull();
    }
}
