package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

class ReviewDeadlinePolicyTest {

    @Test
    void eachPlan_acceptsUpToWhatItPromises() {
        assertThat(ReviewDeadlinePolicy.isValidFor("Premium", ReviewDeadline.hours(1))).isTrue();
        assertThat(ReviewDeadlinePolicy.isValidFor("Premium", ReviewDeadline.hours(48))).isTrue();
        assertThat(ReviewDeadlinePolicy.isValidFor("Premium", ReviewDeadline.hours(49))).isFalse();
        assertThat(ReviewDeadlinePolicy.isValidFor("Premium", ReviewDeadline.businessDays(2))).isFalse();
        assertThat(ReviewDeadlinePolicy.isValidFor("Free", ReviewDeadline.businessDays(5))).isTrue();
        assertThat(ReviewDeadlinePolicy.isValidFor("Free", ReviewDeadline.businessDays(6))).isFalse();
        assertThat(ReviewDeadlinePolicy.isValidFor("Free", ReviewDeadline.hours(24))).isFalse();
        assertThat(ReviewDeadlinePolicy.isValidFor("Other", ReviewDeadline.hours(24))).isFalse();
        assertThat(ReviewDeadlinePolicy.isValidFor("Free", null)).isFalse();
    }

    @Test
    void define_recordsTheDeadlineAndTheSenior() {
        ReviewDeadlinePolicy policy = new ReviewDeadlinePolicy("Premium", ReviewDeadline.hours(24), 7);
        assertThat(policy.getDeadline()).isEqualTo(ReviewDeadline.hours(24));
        assertThat(policy.getUpdatedByUserId()).isEqualTo(7);
        assertThat(policy.getUpdatedAt()).isNotNull();

        policy.define(ReviewDeadline.hours(12), 8);
        assertThat(policy.getDeadline()).isEqualTo(ReviewDeadline.hours(12));
        assertThat(policy.getUpdatedByUserId()).isEqualTo(8);
    }

    @Test
    void invalidPolicies_areRejected() {
        assertThatThrownBy(() -> new ReviewDeadlinePolicy("Gold", ReviewDeadline.hours(24), 7))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ReviewDeadlinePolicy("Free", ReviewDeadline.businessDays(6), 7))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ReviewDeadlinePolicy("Free", ReviewDeadline.businessDays(3), 0))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void defaults_areTheDeadlinesOfThePlans() {
        assertThat(ReviewDeadlinePolicy.defaultFor("Premium")).isEqualTo(ReviewDeadline.hours(48));
        assertThat(ReviewDeadlinePolicy.defaultFor("Free")).isEqualTo(ReviewDeadline.businessDays(5));
    }
}
