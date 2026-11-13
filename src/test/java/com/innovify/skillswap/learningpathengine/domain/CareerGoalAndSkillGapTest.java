package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CareerGoalAndSkillGapTest {

    @Test
    void careerGoal_trimsTheTextAndRemovesDuplicateAndBlankTags() {
        CareerGoal goal = new CareerGoal("  build APIs  ", List.of("rest-api-design", " rest-api-design ", "", "  "));

        assertThat(goal.rawText()).isEqualTo("build APIs");
        assertThat(goal.mappedSkillTags()).containsExactly("rest-api-design");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void careerGoal_withBlankText_throws(String text) {
        assertThatThrownBy(() -> new CareerGoal(text, List.of("rest-api-design")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void careerGoal_withTextOverTheLimit_throws() {
        assertThatThrownBy(() -> new CareerGoal("a".repeat(CareerGoal.MAX_RAW_TEXT_LENGTH + 1),
                List.of("rest-api-design"))).isInstanceOf(DomainException.class);
    }

    @Test
    void careerGoal_withoutAnySkill_throws() {
        assertThatThrownBy(() -> new CareerGoal("build APIs", List.of())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CareerGoal("build APIs", List.of("", " "))).isInstanceOf(DomainException.class);
    }

    @Test
    void skillGap_sortsAndDeduplicatesBothLists() {
        SkillGap gap = new SkillGap(List.of("b", "a", "a"), List.of("z", "y", "y"));

        assertThat(gap.verifiedSkillTags()).containsExactly("a", "b");
        assertThat(gap.missingSkillTags()).containsExactly("y", "z");
        assertThat(gap.isEmpty()).isFalse();
    }

    @Test
    void skillGap_withoutMissingSkills_isEmpty() {
        assertThat(new SkillGap(List.of("a"), List.of()).isEmpty()).isTrue();
    }

    @Test
    void skillGap_withASkillBothVerifiedAndMissing_throws() {
        assertThatThrownBy(() -> new SkillGap(List.of("a"), List.of("a", "b"))).isInstanceOf(DomainException.class);
    }
}
