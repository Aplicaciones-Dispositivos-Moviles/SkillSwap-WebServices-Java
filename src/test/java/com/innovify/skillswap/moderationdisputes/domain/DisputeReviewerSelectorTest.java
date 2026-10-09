package com.innovify.skillswap.moderationdisputes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.moderationdisputes.domain.services.DisputeReviewerSelector;
import com.innovify.skillswap.moderationdisputes.domain.services.ReviewerCandidate;
import com.innovify.skillswap.moderationdisputes.domain.services.ReviewerChoice;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DisputeReviewerSelectorTest {

    private final DisputeReviewerSelector selector = new DisputeReviewerSelector();

    @Test
    void choose_prefersTheLeastLoadedSeniorOverAnyOtherVerifier() {
        var choice = selector.choose(Set.of(), List.of(
                new ReviewerCandidate(2, false, 0),
                new ReviewerCandidate(5, true, 3),
                new ReviewerCandidate(4, true, 1)));

        assertThat(choice).contains(new ReviewerChoice(4, true));
    }

    @Test
    void choose_withoutSeniors_fallsBackToTheLeastLoadedVerifier() {
        var choice = selector.choose(Set.of(), List.of(
                new ReviewerCandidate(6, false, 2),
                new ReviewerCandidate(3, false, 0),
                new ReviewerCandidate(2, false, 0)));

        assertThat(choice).contains(new ReviewerChoice(2, false));
    }

    @Test
    void choose_neverChoosesAPartyOfTheDispute() {
        var choice = selector.choose(Set.of(4), List.of(
                new ReviewerCandidate(4, true, 0),
                new ReviewerCandidate(7, false, 5)));

        assertThat(choice).contains(new ReviewerChoice(7, false));
        assertThat(selector.choose(Set.of(4), List.of(new ReviewerCandidate(4, true, 0)))).isEmpty();
        assertThat(selector.choose(null, null)).isEmpty();
    }
}
