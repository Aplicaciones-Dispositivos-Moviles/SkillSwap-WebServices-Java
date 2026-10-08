package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierCandidate;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerifierMatcherTest {

    private static final String SKILL = "http-basics";

    private final VerifierMatcher matcher = new DefaultVerifierMatcher();

    private static VerifierCandidate candidate(int userId, int openCases) {
        return candidate(userId, openCases, SKILL, true, false);
    }

    private static VerifierCandidate candidate(int userId, int openCases, String skill, boolean available,
                                               boolean revoked) {
        VerifierProfile profile = new VerifierProfile(userId, skill).setAvailability(available);
        if (revoked) {
            profile.revoke();
        }
        return new VerifierCandidate(profile, openCases);
    }

    @Test
    void findVerifier_choosesTheOneWithTheFewestOpenCases() {
        var chosen = matcher.findVerifier(SKILL, 1, List.of(candidate(2, 3), candidate(3, 1), candidate(4, 2)));

        assertThat(chosen).hasValueSatisfying(profile -> assertThat(profile.getVerifierUserId()).isEqualTo(3));
    }

    @Test
    void findVerifier_withATie_choosesTheLowestUserId() {
        var chosen = matcher.findVerifier(SKILL, 1, List.of(candidate(5, 1), candidate(2, 1), candidate(4, 1)));

        assertThat(chosen).hasValueSatisfying(profile -> assertThat(profile.getVerifierUserId()).isEqualTo(2));
    }

    @Test
    void findVerifier_neverChoosesTheStudentOfTheCase() {
        var chosen = matcher.findVerifier(SKILL, 2, List.of(candidate(2, 0), candidate(3, 4)));

        assertThat(chosen).hasValueSatisfying(profile -> assertThat(profile.getVerifierUserId()).isEqualTo(3));
    }

    @Test
    void findVerifier_skipsUnavailableRevokedAndUnqualifiedVerifiers() {
        var chosen = matcher.findVerifier(SKILL, 1, List.of(
                candidate(2, 0, SKILL, false, false),
                candidate(3, 0, SKILL, true, true),
                candidate(4, 0, "sql-fundamentals", true, false),
                candidate(5, 9)));

        assertThat(chosen).hasValueSatisfying(profile -> assertThat(profile.getVerifierUserId()).isEqualTo(5));
    }

    @Test
    void findVerifier_whenNobodyQualifies_returnsEmpty() {
        var chosen = matcher.findVerifier(SKILL, 1, List.of(
                candidate(1, 0),
                candidate(2, 0, SKILL, false, false),
                candidate(3, 0, "sql-fundamentals", true, false)));

        assertThat(chosen).isEmpty();
    }

    @Test
    void findVerifier_withoutCandidates_returnsEmpty() {
        assertThat(matcher.findVerifier(SKILL, 1, List.of())).isEmpty();
    }
}
