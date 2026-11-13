package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.entities.RubricCriterion;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiniProjectTest {

    private static MiniProject miniProject() {
        return new MiniProject(7, " http-basics ", " Build a REST API ", " Build a small REST API. ",
                TestData.rubric());
    }

    private static List<RubricCriterion> criteria(int... weights) {
        List<RubricCriterion> list = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            list.add(new RubricCriterion("Criterion " + i, "Description " + i, weights[i]));
        }
        return list;
    }

    @Test
    void constructor_keepsTheCleanedDataAndAFixedFiveDayDeadline() {
        // The moment is stored with microseconds, so it is compared with the same precision.
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        MiniProject miniProject = miniProject();

        assertThat(miniProject.getPathNodeId()).isEqualTo(7);
        assertThat(miniProject.getSkillTag()).isEqualTo("http-basics");
        assertThat(miniProject.getTitle()).isEqualTo("Build a REST API");
        assertThat(miniProject.getStatement()).isEqualTo("Build a small REST API.");
        assertThat(miniProject.getRubric()).isEqualTo(TestData.rubric());
        assertThat(miniProject.getPassingScore()).isEqualTo(70);
        assertThat(miniProject.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(Duration.between(miniProject.getCreatedAt(), miniProject.getDueAt())).isEqualTo(Duration.ofDays(5));
    }

    @Test
    void constructor_keepsTheMomentsWithTheMicrosecondsPostgresStores() {
        MiniProject miniProject = miniProject();

        assertThat(miniProject.getCreatedAt()).isEqualTo(miniProject.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
        assertThat(miniProject.getDueAt()).isEqualTo(miniProject.getDueAt().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void constructor_fromADraft_buildsTheSameMiniProject() {
        MiniProject miniProject = new MiniProject(7, "http-basics", TestData.miniProjectDraft());

        assertThat(miniProject.getTitle()).isEqualTo("Build a REST API");
        assertThat(miniProject.getRubric()).isEqualTo(TestData.rubric());
    }

    @Test
    void constructor_withoutADraft_throwsDomainException() {
        assertThatThrownBy(() -> new MiniProject(7, "http-basics", null)).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withAnInvalidNodeOrSkill_throwsDomainException() {
        assertThatThrownBy(() -> new MiniProject(0, "http-basics", "T", "S", TestData.rubric()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProject(7, " ", "T", "S", TestData.rubric()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProject(7, null, "T", "S", TestData.rubric()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withAnEmptyOrTooLongTitleOrStatement_throwsDomainException() {
        assertThatThrownBy(() -> new MiniProject(7, "s", " ", "S", TestData.rubric()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProject(7, "s", "t".repeat(MiniProject.MAX_TITLE_LENGTH + 1), "S",
                TestData.rubric())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProject(7, "s", "T", null, TestData.rubric()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProject(7, "s", "T", "x".repeat(MiniProject.MAX_STATEMENT_LENGTH + 1),
                TestData.rubric())).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withARubricOfTheWrongSize_throwsDomainException() {
        assertThatThrownBy(() -> new MiniProject(7, "s", "T", "S", criteria(50, 30, 20)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProject(7, "s", "T", "S", criteria(20, 20, 20, 20, 10, 5, 5)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new MiniProject(7, "s", "T", "S", null)).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withARubricAcceptsFourToSixCriteria() {
        assertThat(new MiniProject(7, "s", "T", "S", criteria(25, 25, 25, 25)).getRubric()).hasSize(4);
        assertThat(new MiniProject(7, "s", "T", "S", criteria(20, 20, 20, 20, 20)).getRubric()).hasSize(5);
        assertThat(new MiniProject(7, "s", "T", "S", criteria(20, 20, 20, 20, 10, 10)).getRubric()).hasSize(6);
    }

    @Test
    void constructor_withWeightsThatDoNotAddUpTo100_throwsDomainException() {
        assertThatThrownBy(() -> new MiniProject(7, "s", "T", "S", criteria(25, 25, 25, 24)))
                .isInstanceOf(DomainException.class).hasMessageContaining("100");
        assertThatThrownBy(() -> new MiniProject(7, "s", "T", "S", criteria(30, 30, 30, 30)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withRepeatedCriteriaNames_throwsDomainException() {
        List<RubricCriterion> repeated = List.of(
                new RubricCriterion("Quality", "A", 25), new RubricCriterion("quality", "B", 25),
                new RubricCriterion("Docs", "C", 25), new RubricCriterion("Tests", "D", 25));

        assertThatThrownBy(() -> new MiniProject(7, "s", "T", "S", repeated)).isInstanceOf(DomainException.class);
    }

    @Test
    void isExpired_isTrueOnlyAfterTheDeadline() {
        MiniProject miniProject = miniProject();

        assertThat(miniProject.isExpired(miniProject.getCreatedAt())).isFalse();
        assertThat(miniProject.isExpired(miniProject.getDueAt())).isFalse();
        assertThat(miniProject.isExpired(miniProject.getDueAt().plusSeconds(1))).isTrue();
    }

    @Test
    void rubricCriterion_validatesItsFields() {
        assertThat(new RubricCriterion(" Quality ", " Clean code. ", 30).getName()).isEqualTo("Quality");
        assertThatThrownBy(() -> new RubricCriterion(" ", "D", 30)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RubricCriterion("N", null, 30)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RubricCriterion("n".repeat(RubricCriterion.MAX_NAME_LENGTH + 1), "D", 30))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RubricCriterion("N", "d".repeat(RubricCriterion.MAX_DESCRIPTION_LENGTH + 1), 30))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RubricCriterion("N", "D", 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RubricCriterion("N", "D", 101)).isInstanceOf(DomainException.class);
    }
}
