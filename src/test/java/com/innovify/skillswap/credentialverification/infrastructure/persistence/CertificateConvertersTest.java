package com.innovify.skillswap.credentialverification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationMethod;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.converters.RiskAssessmentConverter;
import com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.converters.VerificationMethodConverter;
import com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.converters.VerificationStatusConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CertificateConvertersTest {

    private final VerificationStatusConverter statusConverter = new VerificationStatusConverter();
    private final VerificationMethodConverter methodConverter = new VerificationMethodConverter();
    private final RiskAssessmentConverter riskConverter = new RiskAssessmentConverter();

    @ParameterizedTest
    @EnumSource(VerificationStatus.class)
    void status_roundTripsAsThePascalCaseText(VerificationStatus status) {
        String text = statusConverter.convertToDatabaseColumn(status);

        assertThat(text).isEqualTo(status.value());
        assertThat(statusConverter.convertToEntityAttribute(text)).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(VerificationMethod.class)
    void method_roundTripsAsThePascalCaseText(VerificationMethod method) {
        String text = methodConverter.convertToDatabaseColumn(method);

        assertThat(text).isEqualTo(method.value());
        assertThat(methodConverter.convertToEntityAttribute(text)).isEqualTo(method);
    }

    @Test
    void textsWrittenByTheCSharpApi_areRead() {
        assertThat(statusConverter.convertToEntityAttribute("Suspicious")).isEqualTo(VerificationStatus.SUSPICIOUS);
        assertThat(methodConverter.convertToEntityAttribute("OcrOnly")).isEqualTo(VerificationMethod.OCR_ONLY);
    }

    @Test
    void unknownText_isRejected() {
        assertThatThrownBy(() -> statusConverter.convertToEntityAttribute("Whatever"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void risk_isStoredAsItsScoreAndRebuiltFromIt() {
        assertThat(riskConverter.convertToDatabaseColumn(new RiskAssessment(60))).isEqualTo(60);
        assertThat(riskConverter.convertToEntityAttribute(60).level())
                .isEqualTo(new RiskAssessment(60).level());
    }

    @Test
    void nulls_stayNull() {
        assertThat(statusConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(statusConverter.convertToEntityAttribute(null)).isNull();
        assertThat(methodConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(methodConverter.convertToEntityAttribute(null)).isNull();
        assertThat(riskConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(riskConverter.convertToEntityAttribute(null)).isNull();
    }
}
