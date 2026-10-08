package com.innovify.skillswap.iam.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void newUser_startsUnverifiedWithEmptyBioAndNoDeviceToken() {
        User user = TestData.newUser();

        assertThat(user.isVerified()).isFalse();
        assertThat(user.getBio()).isEmpty();
        assertThat(user.getDeviceToken()).isNull();
        assertThat(user.getId()).isNull();
    }

    @Test
    void verify_marksTheUserAsVerified() {
        User user = TestData.newUser();

        user.verify();

        assertThat(user.isVerified()).isTrue();
    }

    @Test
    void updateBio_trimsAndStoresTheText() {
        User user = TestData.newUser();

        user.updateBio("  Backend developer  ");

        assertThat(user.getBio()).isEqualTo("Backend developer");
    }

    @Test
    void updateBio_withExactlyMaxLength_isAccepted() {
        User user = TestData.newUser();

        user.updateBio("a".repeat(User.MAX_BIO_LENGTH));

        assertThat(user.getBio()).hasSize(User.MAX_BIO_LENGTH);
    }

    @Test
    void updateBio_exceedingMaxLength_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.updateBio("a".repeat(User.MAX_BIO_LENGTH + 1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void updateBio_withNull_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.updateBio(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void registerDeviceToken_storesTheToken() {
        User user = TestData.newUser();

        user.registerDeviceToken(" device-123 ");

        assertThat(user.getDeviceToken()).isEqualTo(new DeviceToken("device-123"));
    }

    @Test
    void registerDeviceToken_withBlankToken_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.registerDeviceToken("  ")).isInstanceOf(DomainException.class);
    }
}
