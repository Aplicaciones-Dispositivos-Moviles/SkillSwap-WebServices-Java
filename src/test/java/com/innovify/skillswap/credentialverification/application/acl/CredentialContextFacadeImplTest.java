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
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

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
    void getEvidenceCertificates_withoutCertificates_isEmpty() {
        assertThat(facade.getEvidenceCertificates(7)).isEmpty();
    }
}
