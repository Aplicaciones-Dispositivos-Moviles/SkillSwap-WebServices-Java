package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewDecisionTest {

    @Test
    void value_isThePascalCaseTextTheCSharpApiStores() {
        assertThat(ReviewDecision.APPROVED.value()).isEqualTo("Approved");
        assertThat(ReviewDecision.REJECTED.value()).isEqualTo("Rejected");
        assertThat(CaseStatus.PENDING.value()).isEqualTo("Pending");
        assertThat(CaseStatus.ASSIGNED.value()).isEqualTo("Assigned");
        assertThat(CaseStatus.RESOLVED.value()).isEqualTo("Resolved");
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "APPROVED", " Approved "})
    void tryParse_ignoresCaseAndSurroundingSpaces(String text) {
        assertThat(ReviewDecision.tryParse(text)).contains(ReviewDecision.APPROVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "maybe", "0", "Approve"})
    void tryParse_withAnythingElse_isEmpty(String text) {
        assertThat(ReviewDecision.tryParse(text)).isEmpty();
    }

    @Test
    void tryParse_withNull_isEmpty() {
        assertThat(ReviewDecision.tryParse(null)).isEmpty();
    }

    @Test
    void fromValue_withAnUnknownText_throws() {
        assertThatThrownBy(() -> ReviewDecision.fromValue("maybe")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CaseStatus.fromValue("Closed")).isInstanceOf(IllegalArgumentException.class);
        assertThat(CaseStatus.fromValue("resolved")).isEqualTo(CaseStatus.RESOLVED);
    }
}
