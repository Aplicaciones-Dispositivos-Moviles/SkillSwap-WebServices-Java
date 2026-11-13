package com.innovify.skillswap.credentialverification.domain;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskLevel;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationMethod;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The values are what the database stores and the API exposes, as written by the C# API. */
class VerificationEnumsTest {

    @Test
    void status_valuesArePascalCase() {
        assertThat(VerificationStatus.values()).extracting(VerificationStatus::value)
                .containsExactly("Pending", "Unverified", "Suspicious", "Verified", "Rejected");
    }

    @Test
    void method_valuesArePascalCase() {
        assertThat(VerificationMethod.values()).extracting(VerificationMethod::value)
                .containsExactly("OcrOnly", "Qr", "IssuerUrl", "OfficialRegistry", "Manual");
    }

    @Test
    void riskLevel_valuesArePascalCase() {
        assertThat(RiskLevel.values()).extracting(RiskLevel::value)
                .containsExactly("LowRisk", "Review", "HighRisk");
    }

    @Test
    void fromValue_isCaseInsensitiveAndRoundTrips() {
        for (VerificationStatus status : VerificationStatus.values()) {
            assertThat(VerificationStatus.fromValue(status.value().toLowerCase())).isEqualTo(status);
        }
        assertThat(VerificationMethod.fromValue("OcrOnly")).isEqualTo(VerificationMethod.OCR_ONLY);
        assertThat(RiskLevel.fromValue("HIGHRISK")).isEqualTo(RiskLevel.HIGH_RISK);
    }

    @Test
    void fromValue_withAnUnknownValue_throws() {
        assertThatThrownBy(() -> VerificationStatus.fromValue("Archived"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
