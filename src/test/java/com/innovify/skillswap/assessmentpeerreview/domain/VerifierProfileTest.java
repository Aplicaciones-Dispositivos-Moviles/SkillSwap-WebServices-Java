package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VerifierProfileTest {

    @Test
    void constructor_createsAnAvailableAndEnabledProfileWithTheSkill() {
        VerifierProfile profile = new VerifierProfile(3, " http-basics ");

        assertThat(profile.getVerifierUserId()).isEqualTo(3);
        assertThat(profile.getSkillTags()).containsExactly("http-basics");
        assertThat(profile.isAvailable()).isTrue();
        assertThat(profile.isVerified()).isTrue();
        assertThat(profile.getRating()).isZero();
        assertThat(profile.getReviewCount()).isZero();
        assertThat(profile.getCreatedAt()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"0,skill", "1,' '"})
    void constructor_withInvalidData_throwsDomainException(int userId, String skillTag) {
        assertThatThrownBy(() -> new VerifierProfile(userId, skillTag)).isInstanceOf(DomainException.class);
    }

    @Test
    void addSkill_withANewSkill_addsItKeepingTheListSorted() {
        VerifierProfile profile = new VerifierProfile(3, "rest-api-design");

        boolean added = profile.addSkill("http-basics");

        assertThat(added).isTrue();
        assertThat(profile.getSkillTags()).containsExactly("http-basics", "rest-api-design");
    }

    @Test
    void addSkill_withASkillAlreadyEnabled_changesNothing() {
        VerifierProfile profile = new VerifierProfile(3, "http-basics");

        boolean added = profile.addSkill("http-basics");

        assertThat(added).isFalse();
        assertThat(profile.getSkillTags()).hasSize(1);
    }

    @Test
    void addSkill_withABlankSkill_throwsDomainException() {
        VerifierProfile profile = new VerifierProfile(3, "http-basics");

        assertThatThrownBy(() -> profile.addSkill(" ")).isInstanceOf(DomainException.class);
    }

    @Test
    void canReview_isTrueOnlyForEnabledSkills() {
        VerifierProfile profile = new VerifierProfile(3, "http-basics");

        assertThat(profile.canReview("http-basics")).isTrue();
        assertThat(profile.canReview("sql-fundamentals")).isFalse();
    }

    @Test
    void canReview_afterRevoking_isFalse() {
        VerifierProfile profile = new VerifierProfile(3, "http-basics").revoke();

        assertThat(profile.isVerified()).isFalse();
        assertThat(profile.isAvailable()).isFalse();
        assertThat(profile.canReview("http-basics")).isFalse();
    }

    @Test
    void setAvailability_switchesTheAvailability() {
        VerifierProfile profile = new VerifierProfile(3, "http-basics");

        assertThat(profile.setAvailability(false).isAvailable()).isFalse();
        assertThat(profile.setAvailability(true).isAvailable()).isTrue();
    }

    @Test
    void incrementReviewCount_addsOneEachTime() {
        VerifierProfile profile = new VerifierProfile(3, "http-basics").incrementReviewCount().incrementReviewCount();

        assertThat(profile.getReviewCount()).isEqualTo(2);
    }

    @Test
    void updateRating_keepsTheValue() {
        assertThat(new VerifierProfile(3, "http-basics").updateRating(87.5).getRating()).isEqualTo(87.5);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN})
    void updateRating_withAnInvalidValue_throwsDomainException(double rating) {
        VerifierProfile profile = new VerifierProfile(3, "http-basics");

        assertThatThrownBy(() -> profile.updateRating(rating)).isInstanceOf(DomainException.class);
    }
}
