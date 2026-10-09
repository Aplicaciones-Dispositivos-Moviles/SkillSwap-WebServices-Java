package com.innovify.skillswap.credentialverification.application.acl;

import com.innovify.skillswap.credentialverification.TestData;
import com.innovify.skillswap.credentialverification.application.fakes.FakeCertificateRepository;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialContextFacadeImplTest {

    private final FakeCertificateRepository repository = new FakeCertificateRepository();
    private final CredentialContextFacadeImpl facade = new CredentialContextFacadeImpl(repository);

    private Certificate stored(int ownerId, String hash, int riskScore) {
        Certificate certificate = TestData.newCertificate(ownerId, hash).assessRisk(new RiskAssessment(riskScore));
        return repository.save(certificate);
    }

    @Test
    void getEvidenceCertificates_keepsOnlyUnverifiedAndVerified() {
        Certificate unverified = stored(7, "h1", 0);
        Certificate suspicious = stored(7, "h2", 60);
        Certificate rejected = stored(7, "h3", 60).resolveDispute(false);
        Certificate verified = stored(7, "h4", 60).resolveDispute(true);

        List<CertificateSummary> result = facade.getEvidenceCertificates(7);

        assertThat(result).extracting(CertificateSummary::id)
                .containsExactly(unverified.getId(), verified.getId())
                .doesNotContain(suspicious.getId(), rejected.getId());
    }

    @Test
    void getEvidenceCertificates_returnsOnlyTheOwnersOldestFirst() {
        Certificate first = stored(7, "h1", 0);
        stored(8, "h2", 0);
        Certificate second = stored(7, "h3", 0);

        List<CertificateSummary> result = facade.getEvidenceCertificates(7);

        assertThat(result).extracting(CertificateSummary::id).containsExactly(first.getId(), second.getId());
    }

    @Test
    void getEvidenceCertificates_exposesOnlyTheSummary() {
        stored(7, "h1", 0);
        Certificate withoutData = repository.save(
                new Certificate(7, "h2", "ref").applyExtractedData(null, null, null, (LocalDate) null, null,
                        null, null, null, null, null).assessRisk(new RiskAssessment(15)));

        List<CertificateSummary> result = facade.getEvidenceCertificates(7);

        assertThat(result.get(0).courseName()).isEqualTo("Backend with Spring");
        assertThat(result.get(0).institutionName()).isEqualTo("Coursera");
        assertThat(result.get(1).id()).isEqualTo(withoutData.getId());
        assertThat(result.get(1).courseName()).isNull();
        assertThat(result.get(1).institutionName()).isNull();
    }

    @Test
    void getValidatedCertificates_keepsOnlyTheVerifiedOnesOfTheOwnerOldestFirst() {
        stored(7, "h1", 0); // unverified: supporting evidence, not validated
        Certificate second = stored(7, "h2", 60).resolveDispute(true);
        stored(7, "h3", 60); // suspicious
        stored(8, "h4", 60).resolveDispute(true); // another student
        Certificate first = stored(7, "h5", 60).resolveDispute(true);

        List<CertificateEvidence> result = facade.getValidatedCertificates(7);

        assertThat(result).extracting(CertificateEvidence::id).containsExactly(second.getId(), first.getId());
        assertThat(result).allSatisfy(evidence -> {
            assertThat(evidence.validated()).isTrue();
            assertThat(evidence.supportingEvidence()).isTrue();
            assertThat(evidence.ownerId()).isEqualTo(7);
        });
    }

    @Test
    void getCertificate_exposesTheExtractedDataAndWhetherItIsEvidence() {
        Certificate unverified = stored(7, "h1", 0);
        Certificate suspicious = stored(7, "h2", 60);
        Certificate verified = stored(7, "h3", 60).resolveDispute(true);

        CertificateEvidence evidence = facade.getCertificate(unverified.getId()).orElseThrow();
        assertThat(evidence.ownerId()).isEqualTo(7);
        assertThat(evidence.courseName()).isEqualTo("Backend with Spring");
        assertThat(evidence.ocrText()).isEqualTo(unverified.getOcrText());
        assertThat(evidence.supportingEvidence()).isTrue();
        assertThat(evidence.validated()).isFalse();

        assertThat(facade.getCertificate(suspicious.getId()).orElseThrow().supportingEvidence()).isFalse();
        assertThat(facade.getCertificate(verified.getId()).orElseThrow().validated()).isTrue();
    }

    @Test
    void getCertificate_unknown_isEmpty() {
        assertThat(facade.getCertificate(999)).isEqualTo(Optional.empty());
    }

    @Test
    void getEvidenceCertificates_withoutCertificates_isEmpty() {
        assertThat(facade.getEvidenceCertificates(7)).isEmpty();
    }
}
