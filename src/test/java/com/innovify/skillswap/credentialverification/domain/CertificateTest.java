package com.innovify.skillswap.credentialverification.domain;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationMethod;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

    private static Certificate newPending() {
        return new Certificate(1, "ABCDEF", "certificates/1/file");
    }

    private static Certificate newWithCompleteData() {
        return newPending().applyExtractedData("Ana Perez", "Coursera", "Backend with Spring",
                LocalDate.of(2025, 3, 10), 40, "cert-001", "code-xyz", "https://example.com/verify/1", "qr",
                "full text");
    }

    private static Certificate newSuspicious() {
        return newWithCompleteData().assessRisk(new RiskAssessment(60));
    }

    // ---------- Creation ----------

    @Test
    void newCertificate_startsPendingWithOcrOnlyMethodAndNoRisk() {
        Certificate certificate = newPending();

        assertThat(certificate.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(certificate.getVerificationMethod()).isEqualTo(VerificationMethod.OCR_ONLY);
        assertThat(certificate.getRiskAssessment()).isNull();
        assertThat(certificate.getVerifiedAt()).isNull();
        assertThat(certificate.getCreatedAt()).isNotNull();
        assertThat(certificate.getFileHash()).isEqualTo("abcdef");
        assertThat(certificate.getOcrText()).isEmpty();
        assertThat(certificate.getId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "0,hash,ref",
            "-1,hash,ref",
            "1,'',ref",
            "1,'  ',ref",
            "1,hash,''"})
    void constructor_withInvalidValues_throwsDomainException(int ownerId, String hash, String reference) {
        assertThatThrownBy(() -> new Certificate(ownerId, hash, reference)).isInstanceOf(DomainException.class);
    }

    // ---------- Extracted data ----------

    @Test
    void applyExtractedData_trimsValuesAndUppercasesNumberAndCode() {
        Certificate certificate = newPending().applyExtractedData("  Ana Perez ", " Coursera", "Course", null, null,
                " cert-001 ", "code-xyz", null, null, null);

        assertThat(certificate.getHolderName()).isEqualTo("Ana Perez");
        assertThat(certificate.getInstitutionName()).isEqualTo("Coursera");
        assertThat(certificate.getCertificateNumber()).isEqualTo("CERT-001");
        assertThat(certificate.getVerificationCode()).isEqualTo("CODE-XYZ");
    }

    @Test
    void applyExtractedData_storesBlankValuesAsNullAndEmptyOcrText() {
        Certificate certificate = newPending().applyExtractedData("  ", "", null, null, null, " ", null, "", null,
                "   ");

        assertThat(certificate.getHolderName()).isNull();
        assertThat(certificate.getInstitutionName()).isNull();
        assertThat(certificate.getCertificateNumber()).isNull();
        assertThat(certificate.getVerificationUrl()).isNull();
        assertThat(certificate.getOcrText()).isEmpty();
    }

    @Test
    void applyExtractedData_withTextOverTheLimit_throwsDomainException() {
        String tooLong = "a".repeat(Certificate.MAX_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> newPending()
                .applyExtractedData(tooLong, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void applyExtractedData_onANonPendingCertificate_throwsDomainException() {
        Certificate certificate = newSuspicious();

        assertThatThrownBy(() -> certificate
                .applyExtractedData(null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(DomainException.class);
    }

    // ---------- OCR inconsistencies ----------

    @Test
    void hasOcrInconsistencies_withCompleteAndValidData_returnsFalse() {
        assertThat(newWithCompleteData().hasOcrInconsistencies(TODAY)).isFalse();
    }

    @Test
    void hasOcrInconsistencies_whenDurationIsMissing_returnsFalse() {
        Certificate certificate = newPending().applyExtractedData("Ana", "Coursera", "Course",
                LocalDate.of(2025, 3, 10), null, null, null, null, null, null);

        assertThat(certificate.hasOcrInconsistencies(TODAY)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null,Coursera,Course,true",
            "Ana,null,Course,true",
            "Ana,Coursera,null,true",
            "Ana,Coursera,Course,false"}, nullValues = "null")
    void hasOcrInconsistencies_whenHolderInstitutionCourseOrDateIsMissing_returnsTrue(
            String holder, String institution, String course, boolean hasIssueDate) {
        Certificate certificate = newPending().applyExtractedData(holder, institution, course,
                hasIssueDate ? LocalDate.of(2025, 3, 10) : null, null, null, null, null, null, null);

        boolean complete = holder != null && institution != null && course != null && hasIssueDate;
        assertThat(certificate.hasOcrInconsistencies(TODAY)).isEqualTo(!complete);
    }

    @Test
    void hasOcrInconsistencies_withFutureIssueDate_returnsTrue() {
        Certificate certificate = newPending().applyExtractedData("Ana", "Coursera", "Course", TODAY.plusDays(1),
                10, null, null, null, null, null);

        assertThat(certificate.hasOcrInconsistencies(TODAY)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void hasOcrInconsistencies_withNonPositiveDuration_returnsTrue(int hours) {
        Certificate certificate = newPending().applyExtractedData("Ana", "Coursera", "Course",
                LocalDate.of(2025, 3, 10), hours, null, null, null, null, null);

        assertThat(certificate.hasOcrInconsistencies(TODAY)).isTrue();
    }

    // ---------- Risk assessment ----------

    @ParameterizedTest
    @CsvSource({"0,UNVERIFIED", "30,UNVERIFIED", "49,UNVERIFIED", "50,SUSPICIOUS", "85,SUSPICIOUS"})
    void assessRisk_setsTheStatusFromTheRiskLevel(int score, VerificationStatus expected) {
        Certificate certificate = newWithCompleteData().assessRisk(new RiskAssessment(score));

        assertThat(certificate.getStatus()).isEqualTo(expected);
        assertThat(certificate.getRiskAssessment().score()).isEqualTo(score);
        assertThat(certificate.getVerifiedAt()).isNull();
    }

    @Test
    void assessRisk_twice_throwsDomainException() {
        Certificate certificate = newWithCompleteData().assessRisk(new RiskAssessment(0));

        assertThatThrownBy(() -> certificate.assessRisk(new RiskAssessment(60)))
                .isInstanceOf(DomainException.class);
    }

    // ---------- Dispute resolution ----------

    @ParameterizedTest
    @CsvSource({"true,VERIFIED", "false,REJECTED"})
    void resolveDispute_onASuspiciousCertificate_setsTheFinalStatus(boolean isAuthentic,
                                                                    VerificationStatus expected) {
        Certificate certificate = newSuspicious().resolveDispute(isAuthentic);

        assertThat(certificate.getStatus()).isEqualTo(expected);
        assertThat(certificate.getVerificationMethod()).isEqualTo(VerificationMethod.MANUAL);
        assertThat(certificate.getVerifiedAt()).isNotNull();
    }

    @Test
    void resolveDispute_onAPendingCertificate_throwsDomainException() {
        assertThatThrownBy(() -> newPending().resolveDispute(true)).isInstanceOf(DomainException.class);
    }

    @Test
    void resolveDispute_onAnUnverifiedCertificate_throwsDomainException() {
        Certificate certificate = newWithCompleteData().assessRisk(new RiskAssessment(0));

        assertThatThrownBy(() -> certificate.resolveDispute(true)).isInstanceOf(DomainException.class);
    }

    @Test
    void resolveDispute_twice_throwsDomainException() {
        Certificate certificate = newSuspicious().resolveDispute(true);

        assertThatThrownBy(() -> certificate.resolveDispute(false)).isInstanceOf(DomainException.class);
    }

    @org.junit.jupiter.api.Test
    void flagHolderNameMismatch_onlyOnAPendingCertificateWithAHolder() {
        var certificate = com.innovify.skillswap.credentialverification.TestData.newCertificate(7, "abc");
        org.assertj.core.api.Assertions.assertThat(certificate.hasHolderNameMismatch()).isFalse();

        certificate.flagHolderNameMismatch();
        org.assertj.core.api.Assertions.assertThat(certificate.hasHolderNameMismatch()).isTrue();

        var withoutHolder = new com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate(
                7, "def", "ref").applyExtractedData(null, null, null, null, null, null, null, null, null, null);
        org.assertj.core.api.Assertions.assertThatThrownBy(withoutHolder::flagHolderNameMismatch)
                .isInstanceOf(com.innovify.skillswap.shared.domain.exceptions.DomainException.class);

        certificate.assessRisk(new com.innovify.skillswap.credentialverification.domain.model.valueobjects
                .RiskAssessment(50));
        org.assertj.core.api.Assertions.assertThatThrownBy(certificate::flagHolderNameMismatch)
                .isInstanceOf(com.innovify.skillswap.shared.domain.exceptions.DomainException.class);
    }
}
