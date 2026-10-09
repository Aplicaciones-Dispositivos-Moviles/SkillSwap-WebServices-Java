package com.innovify.skillswap.assessmentpeerreview.application.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

class VerifierProfileContextFacadeImplTest {

    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final VerifierProfileContextFacadeImpl facade = new VerifierProfileContextFacadeImpl(profiles, cases);

    @Test
    void isEnabledVerifier_isTrueOnlyForAProfileThatWasNotRevoked() {
        profiles.save(new VerifierProfile(2, "http-basics"));
        profiles.save(new VerifierProfile(3, "http-basics").revoke());

        assertThat(facade.isEnabledVerifier(2)).isTrue();
        assertThat(facade.isEnabledVerifier(3)).isFalse();
        assertThat(facade.isEnabledVerifier(4)).isFalse();
    }

    @Test
    void getAvailableVerifiers_leavesOutTheUnavailableAndRevokedOnesAndCountsTheirOpenCases() {
        profiles.save(new VerifierProfile(5, "http-basics"));
        profiles.save(new VerifierProfile(2, "sql-basics"));
        profiles.save(new VerifierProfile(3, "http-basics").setAvailability(false));
        profiles.save(new VerifierProfile(4, "http-basics").revoke());
        cases.save(new VerificationCase(1, 9, 1, "http-basics", CaseType.QUIZ).assignVerifier(5));

        assertThat(facade.getAvailableVerifiers())
                .containsExactly(new VerifierWorkload(2, 0), new VerifierWorkload(5, 1));
    }

    @Test
    void updateRating_ofAVerifier_storesItAndSaves() {
        VerifierProfile profile = profiles.save(new VerifierProfile(2, "http-basics"));
        int savesBefore = profiles.saveCalls();

        boolean updated = facade.updateRating(2, 85);

        assertThat(updated).isTrue();
        assertThat(profile.getRating()).isEqualTo(85);
        assertThat(profiles.saveCalls()).isEqualTo(savesBefore + 1);
    }

    @Test
    void updateRating_doesNotTouchTheReviewCount() {
        VerifierProfile profile = profiles.save(
                new VerifierProfile(2, "http-basics").incrementReviewCount().incrementReviewCount());

        facade.updateRating(2, 70);

        assertThat(profile.getReviewCount()).isEqualTo(2);
    }

    @Test
    void updateRating_onlyChangesTheProfileOfThatUser() {
        VerifierProfile target = profiles.save(new VerifierProfile(2, "http-basics"));
        VerifierProfile other = profiles.save(new VerifierProfile(3, "http-basics"));

        facade.updateRating(2, 60);

        assertThat(target.getRating()).isEqualTo(60);
        assertThat(other.getRating()).isZero();
    }

    @Test
    void updateRating_forAUserWithoutAProfile_returnsFalseAndSavesNothing() {
        boolean updated = facade.updateRating(99, 85);

        assertThat(updated).isFalse();
        assertThat(profiles.saveCalls()).isZero();
    }

    @Test
    void updateRating_ofARevokedProfile_stillStoresIt() {
        VerifierProfile profile = profiles.save(new VerifierProfile(2, "http-basics").revoke());

        boolean updated = facade.updateRating(2, 40);

        assertThat(updated).isTrue();
        assertThat(profile.getRating()).isEqualTo(40);
    }

    @Test
    void updateRating_withAnInvalidRating_throwsAndSavesNothing() {
        profiles.save(new VerifierProfile(2, "http-basics"));
        int savesBefore = profiles.saveCalls();

        assertThatThrownBy(() -> facade.updateRating(2, -1)).isInstanceOf(DomainException.class);
        assertThat(profiles.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void updateRating_whenPersistenceFails_propagates() {
        profiles.save(new VerifierProfile(2, "http-basics"));
        profiles.failOnSave(new IllegalStateException("database down"));

        assertThatThrownBy(() -> facade.updateRating(2, 80)).isInstanceOf(IllegalStateException.class);
    }
}
