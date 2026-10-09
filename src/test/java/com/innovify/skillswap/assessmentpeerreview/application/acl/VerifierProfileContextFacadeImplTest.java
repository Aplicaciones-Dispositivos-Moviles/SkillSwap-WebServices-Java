package com.innovify.skillswap.assessmentpeerreview.application.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

class VerifierProfileContextFacadeImplTest {

    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final VerifierProfileContextFacadeImpl facade = new VerifierProfileContextFacadeImpl(profiles);

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
