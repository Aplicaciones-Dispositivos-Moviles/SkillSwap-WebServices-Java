package com.innovify.skillswap.credentialverification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskLevel;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationMethod;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.sql.SQLException;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class CertificatePersistenceTest extends PostgresIntegrationTest {

    @Autowired
    private CertificateRepository repository;

    private static Certificate assessed(int ownerId, String hash, String number, String code, int score) {
        return new Certificate(ownerId, hash, "certificates/" + ownerId + "/" + hash + ".jpg")
                .applyExtractedData("Ana Perez", "Coursera", "Backend", LocalDate.of(2025, 3, 10), 40, number,
                        code, "https://example.com/verify/1", "qr-payload", "full ocr text")
                .assessRisk(new RiskAssessment(score));
    }

    private Certificate saveAssessed(int ownerId, String hash) {
        return repository.save(assessed(ownerId, hash, null, null, 0));
    }

    @Test
    void certificate_roundTripsEveryField() {
        Certificate saved = repository.save(assessed(7, "hash-a", "cert-001", "code-xyz", 60));

        Certificate loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getOwnerId()).isEqualTo(7);
        assertThat(loaded.getHolderName()).isEqualTo("Ana Perez");
        assertThat(loaded.getInstitutionName()).isEqualTo("Coursera");
        assertThat(loaded.getCourseName()).isEqualTo("Backend");
        assertThat(loaded.getIssueDate()).isEqualTo(LocalDate.of(2025, 3, 10));
        assertThat(loaded.getDurationHours()).isEqualTo(40);
        assertThat(loaded.getCertificateNumber()).isEqualTo("CERT-001");
        assertThat(loaded.getVerificationCode()).isEqualTo("CODE-XYZ");
        assertThat(loaded.getVerificationUrl()).isEqualTo("https://example.com/verify/1");
        assertThat(loaded.getQrPayload()).isEqualTo("qr-payload");
        assertThat(loaded.getOcrText()).isEqualTo("full ocr text");
        assertThat(loaded.getFileHash()).isEqualTo("hash-a");
        assertThat(loaded.getStorageReference()).isEqualTo("certificates/7/hash-a.jpg");
        assertThat(loaded.getStatus()).isEqualTo(VerificationStatus.SUSPICIOUS);
        assertThat(loaded.getVerificationMethod()).isEqualTo(VerificationMethod.OCR_ONLY);
        assertThat(loaded.getRiskAssessment().score()).isEqualTo(60);
        assertThat(loaded.getRiskAssessment().level()).isEqualTo(RiskLevel.HIGH_RISK);
        assertThat(loaded.getCreatedAt()).isBetween(Instant.now().minus(Duration.ofMinutes(1)), Instant.now());
        assertThat(loaded.getVerifiedAt()).isNull();
    }

    @Test
    void pendingCertificate_withoutRiskOrOcrData_roundTripsWithNulls() {
        Certificate saved = repository.save(new Certificate(1, "hash-a", "certificates/1/hash-a.jpg"));

        Certificate loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(loaded.getRiskAssessment()).isNull();
        assertThat(loaded.getHolderName()).isNull();
        assertThat(loaded.getIssueDate()).isNull();
        assertThat(loaded.getDurationHours()).isNull();
        assertThat(loaded.getCertificateNumber()).isNull();
        assertThat(loaded.getOcrText()).isEmpty();
    }

    @Test
    void enumsAndRisk_areStoredAsTheTextAndNumberTheCSharpApiWrites() throws SQLException {
        repository.save(assessed(1, "hash-a", null, null, 60));

        assertThat(queryString("SELECT status FROM certificates")).isEqualTo("Suspicious");
        assertThat(queryString("SELECT verification_method FROM certificates")).isEqualTo("OcrOnly");
        assertThat(queryString("SELECT risk_score FROM certificates")).isEqualTo("60");
    }

    @Test
    void database_rejectsTheSameFileTwiceForTheSameOwner() {
        saveAssessed(1, "hash-a");

        assertThatThrownBy(() -> saveAssessed(1, "hash-a")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_allowsTheSameFileForDifferentOwners() {
        saveAssessed(1, "hash-a");

        Certificate second = saveAssessed(2, "hash-a");

        assertThat(second.getId()).isPositive();
    }

    @Test
    void existsQueries_distinguishTheOwnerFromTheOthers() {
        repository.save(assessed(1, "hash-a", "cert-001", "code-xyz", 0));

        assertThat(repository.findByFileHash(1, "hash-a")).get().extracting(Certificate::getOwnerId).isEqualTo(1);
        assertThat(repository.findByFileHash(2, "hash-a")).isEmpty();

        assertThat(repository.existsByFileHashExcludingOwner(2, "hash-a")).isTrue();
        assertThat(repository.existsByFileHashExcludingOwner(1, "hash-a")).isFalse();

        assertThat(repository.existsByCertificateNumberExcludingOwner(2, "CERT-001")).isTrue();
        assertThat(repository.existsByCertificateNumberExcludingOwner(1, "CERT-001")).isFalse();
        assertThat(repository.existsByCertificateNumberExcludingOwner(2, "CERT-999")).isFalse();

        assertThat(repository.existsByVerificationCodeExcludingOwner(2, "CODE-XYZ")).isTrue();
        assertThat(repository.existsByVerificationCodeExcludingOwner(1, "CODE-XYZ")).isFalse();
    }

    @Test
    void findByOwnerId_returnsOnlyThatOwnersCertificatesNewestFirst() {
        Certificate first = saveAssessed(1, "hash-a");
        saveAssessed(2, "hash-b");
        Certificate third = saveAssessed(1, "hash-c");

        List<Certificate> found = repository.findByOwnerId(1);

        assertThat(found).extracting(Certificate::getId).containsExactly(third.getId(), first.getId());
    }

    @Test
    void resolvedDispute_isPersisted() {
        Certificate saved = repository.save(assessed(1, "hash-a", null, null, 60));

        Certificate certificate = repository.findById(saved.getId()).orElseThrow();
        certificate.resolveDispute(true);
        repository.save(certificate);

        Certificate loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(loaded.getVerificationMethod()).isEqualTo(VerificationMethod.MANUAL);
        assertThat(loaded.getVerifiedAt()).isNotNull();
    }
}
