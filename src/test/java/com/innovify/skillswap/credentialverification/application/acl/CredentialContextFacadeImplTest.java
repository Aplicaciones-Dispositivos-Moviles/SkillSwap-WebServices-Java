package com.innovify.skillswap.credentialverification.application.acl;

import com.innovify.skillswap.credentialverification.TestData;
import com.innovify.skillswap.credentialverification.application.fakes.FakeCertificateRepository;
import com.innovify.skillswap.credentialverification.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.credentialverification.application.fakes.FakeFileStorageService;
import com.innovify.skillswap.credentialverification.application.fakes.FakeIamContextFacade;
import com.innovify.skillswap.credentialverification.application.internal.commandservices.CertificateCommandServiceImpl;
import com.innovify.skillswap.credentialverification.application.internal.queryservices.CertificateQueryServiceImpl;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.credentialverification.domain.services.DefaultCertificateRiskScorer;
import com.innovify.skillswap.credentialverification.domain.services.HolderNameMatcher;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialContextFacadeImplTest {

    private final FakeCertificateRepository repository = new FakeCertificateRepository();
    private final FakeFileStorageService storage = new FakeFileStorageService();
    private final CredentialContextFacadeImpl facade = new CredentialContextFacadeImpl(repository,
            new CertificateCommandServiceImpl(repository, new DefaultCertificateRiskScorer(), new HolderNameMatcher(),
                    storage, new FakeIamContextFacade(), new FakeDomainEventPublisher(), TestMessagesHolder.SOURCE),
            new CertificateQueryServiceImpl(repository, storage));

    /** The real message bundles. */
    private static final class TestMessagesHolder {
        static final org.springframework.context.support.ResourceBundleMessageSource SOURCE = create();

        private static org.springframework.context.support.ResourceBundleMessageSource create() {
            var messages = new org.springframework.context.support.ResourceBundleMessageSource();
            messages.setBasename("messages");
            messages.setDefaultEncoding("UTF-8");
            messages.setFallbackToSystemLocale(false);
            return messages;
        }
    }

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

    // ---------- getMostFrequentCertifications ----------

    private Certificate uploaded(int ownerId, String hash, String courseName, String createdAt) {
        Certificate certificate = new Certificate(ownerId, hash, "certificates/" + ownerId + "/" + hash + ".pdf")
                .applyExtractedData("Ana Perez", "Coursera", courseName, LocalDate.of(2025, 3, 10), 40,
                        "cert-" + hash, "code-" + hash, "https://example.com/verify/" + hash, null, "full text");
        ReflectionTestUtils.setField(certificate, "createdAt", Instant.parse(createdAt));
        return repository.save(certificate);
    }

    @Test
    void getMostFrequentCertifications_groupsTheCoursesIgnoringCaseAndSpaces() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        uploaded(1, "h1", "Backend with Spring", "2026-10-02T10:00:00Z");
        uploaded(2, "h2", "  backend   WITH spring ", "2026-10-03T10:00:00Z");
        uploaded(3, "h3", "SQL basics", "2026-10-04T10:00:00Z");
        uploaded(4, "h4", "Docker", "2026-10-05T10:00:00Z");
        uploaded(5, "h5", "Docker", "2026-10-06T10:00:00Z");
        uploaded(6, "h6", "Docker", "2026-09-30T23:59:59Z");
        uploaded(7, "h7", "Docker", "2026-11-01T00:00:00Z");

        List<CertificationDemandView> result = facade.getMostFrequentCertifications(from, to, 10);

        assertThat(result).extracting(CertificationDemandView::courseName, CertificationDemandView::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Backend with Spring", 2),
                        org.assertj.core.groups.Tuple.tuple("Docker", 2),
                        org.assertj.core.groups.Tuple.tuple("SQL basics", 1));
    }

    @Test
    void getMostFrequentCertifications_leavesOutTheRejectedAndTheOnesWithoutCourseName() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        uploaded(1, "h1", "Docker", "2026-10-02T10:00:00Z");
        uploaded(2, "h2", "Docker", "2026-10-03T10:00:00Z")
                .assessRisk(new RiskAssessment(60)).resolveDispute(false);
        Certificate unnamed = new Certificate(3, "h3", "certificates/3/h3.pdf");
        ReflectionTestUtils.setField(unnamed, "createdAt", Instant.parse("2026-10-04T10:00:00Z"));
        repository.save(unnamed);

        List<CertificationDemandView> result = facade.getMostFrequentCertifications(from, to, 10);

        assertThat(result).extracting(CertificationDemandView::courseName, CertificationDemandView::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Docker", 1));
    }

    @Test
    void getMostFrequentCertifications_respectsTheLimitAndAnEmptyPeriod() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        uploaded(1, "h1", "Docker", "2026-10-02T10:00:00Z");
        uploaded(2, "h2", "Docker", "2026-10-03T10:00:00Z");
        uploaded(3, "h3", "SQL basics", "2026-10-04T10:00:00Z");

        assertThat(facade.getMostFrequentCertifications(from, to, 1)).extracting(CertificationDemandView::courseName)
                .containsExactly("Docker");
        assertThat(facade.getMostFrequentCertifications(from, to, 0)).isEmpty();
        assertThat(facade.getMostFrequentCertifications(to, from, 10)).isEmpty();
        assertThat(facade.getMostFrequentCertifications(null, to, 10)).isEmpty();
    }
}
