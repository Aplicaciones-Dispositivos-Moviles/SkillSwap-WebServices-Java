package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillGapAnalyzerTest {

    private final DefaultSkillGapAnalyzer analyzer = new DefaultSkillGapAnalyzer(TestData.TAXONOMY);

    @Test
    void analyze_withNothingVerified_requiresTheGoalAndAllItsPrerequisites() {
        SkillGap gap = analyzer.analyze(TestData.goal("authentication-jwt"), List.of());

        assertThat(gap.missingSkillTags()).containsExactly("authentication-jwt", "http-basics", "networking-basics",
                "programming-fundamentals", "rest-api-design");
        assertThat(gap.verifiedSkillTags()).isEmpty();
    }

    @Test
    void analyze_whenAnIntermediateSkillIsVerified_doesNotRequireItsPrerequisites() {
        SkillGap gap = analyzer.analyze(TestData.goal("authentication-jwt"), List.of("rest-api-design"));

        assertThat(gap.missingSkillTags()).containsExactly("authentication-jwt");
        assertThat(gap.verifiedSkillTags()).containsExactly("rest-api-design");
    }

    @Test
    void analyze_keepsRequiringAPrerequisiteReachableThroughAnotherSkill() {
        SkillGap gap = analyzer.analyze(TestData.goal("authentication-jwt", "http-basics"),
                List.of("rest-api-design"));

        assertThat(gap.missingSkillTags()).containsExactly("authentication-jwt", "http-basics", "networking-basics");
    }

    @Test
    void analyze_ignoresVerifiedSkillsUnrelatedToTheGoal() {
        SkillGap gap = analyzer.analyze(TestData.goal("http-basics"), List.of("sql-fundamentals"));

        assertThat(gap.missingSkillTags()).containsExactly("http-basics", "networking-basics");
        assertThat(gap.verifiedSkillTags()).isEmpty();
    }

    @Test
    void analyze_whenTheGoalIsAlreadyVerified_returnsAnEmptyGap() {
        SkillGap gap = analyzer.analyze(TestData.goal("authentication-jwt"), List.of("authentication-jwt"));

        assertThat(gap.isEmpty()).isTrue();
        assertThat(gap.verifiedSkillTags()).containsExactly("authentication-jwt");
    }

    @Test
    void analyze_withSeveralGoalSkillsSharingPrerequisites_listsThemOnce() {
        SkillGap gap = analyzer.analyze(TestData.goal("rest-api-design", "http-basics"), List.of());

        assertThat(gap.missingSkillTags()).containsExactly("http-basics", "networking-basics",
                "programming-fundamentals", "rest-api-design");
    }

    @Test
    void analyze_withASkillOutsideTheTaxonomy_throws() {
        assertThatThrownBy(() -> analyzer.analyze(TestData.goal("unknown-skill"), List.of()))
                .isInstanceOf(DomainException.class);
    }
}
