package com.innovify.skillswap.iam.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.infrastructure.verification.EmailVerificationSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmailSettingsTest {

    @Test
    void blankValuesMeanNotConfiguredAndTheNameHasADefault() {
        EmailSettings settings = new EmailSettings(" ", "", " ", null, 5, 10);

        assertThat(settings.hasBrevoApiKey()).isFalse();
        assertThat(settings.hasSenderAddress()).isFalse();
        assertThat(settings.senderName()).isEqualTo("SkillSwap");
        assertThat(settings.brevoBaseUrl()).isEqualTo("https://api.brevo.com/v3/");
    }

    @Test
    void toString_neverPrintsTheApiKey() {
        EmailSettings settings = new EmailSettings("xkeysib-secret", "a@b.pe", "SkillSwap",
                "https://api.brevo.com/v3", 5, 10);

        assertThat(settings.brevoBaseUrl()).isEqualTo("https://api.brevo.com/v3/");
        assertThat(settings.toString()).doesNotContain("xkeysib-secret").contains("***");
    }

    @Test
    void nonPositiveTimeoutsAreRejected() {
        assertThatThrownBy(() -> new EmailSettings(null, null, null, null, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verificationSettings_defaultToTheDeployedBackendAndValidateTheirValues() {
        EmailVerificationSettings defaults = new EmailVerificationSettings(" ", Duration.ofHours(24),
                Duration.ofMinutes(2));
        assertThat(defaults.baseUrl()).isEqualTo("https://skillswap-webservices-java.onrender.com");

        assertThatThrownBy(() -> new EmailVerificationSettings("ftp://x", Duration.ofHours(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailVerificationSettings(null, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailVerificationSettings(null, Duration.ofHours(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
