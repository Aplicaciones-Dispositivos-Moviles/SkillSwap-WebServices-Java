package com.innovify.skillswap.credentialverification.application.internal.queryservices;

import com.innovify.skillswap.credentialverification.TestData;
import com.innovify.skillswap.credentialverification.application.fakes.FakeCertificateRepository;
import com.innovify.skillswap.credentialverification.application.fakes.FakeFileStorageService;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificateByIdQuery;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificatesByOwnerIdQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateQueryServiceImplTest {

    private final FakeCertificateRepository repository = new FakeCertificateRepository();
    private final CertificateQueryServiceImpl service =
            new CertificateQueryServiceImpl(repository, new FakeFileStorageService());

    @Test
    void getById_returnsTheCertificate() {
        Certificate saved = repository.save(TestData.newCertificate(7, "hash-a"));

        Optional<Certificate> found = service.handle(new GetCertificateByIdQuery(saved.getId()));

        assertThat(found).containsSame(saved);
    }

    @Test
    void getById_ofUnknownCertificate_isEmpty() {
        assertThat(service.handle(new GetCertificateByIdQuery(99))).isEmpty();
    }

    @Test
    void getByOwner_returnsOnlyThatOwnersCertificates() {
        Certificate mine = repository.save(TestData.newCertificate(7, "hash-a"));
        repository.save(TestData.newCertificate(8, "hash-b"));

        List<Certificate> found = service.handle(new GetCertificatesByOwnerIdQuery(7));

        assertThat(found).containsExactly(mine);
    }

    @Test
    void getFileUrl_buildsATemporaryUrlValidForFifteenMinutes() {
        Certificate certificate = TestData.newCertificate(7, "hash-a");
        // storage reference of the test builder is "certificates/7/hash-a.pdf"
        String url = service.getFileUrl(certificate);

        assertThat(url).isEqualTo("https://files.test/certificates/7/hash-a.pdf?minutes=15");
    }
}
