package com.innovify.skillswap.credentialverification.application.acl;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CredentialContextFacadeImpl implements CredentialContextFacade {

    private static final Set<VerificationStatus> EVIDENCE_STATUSES =
            Set.of(VerificationStatus.UNVERIFIED, VerificationStatus.VERIFIED);

    private final CertificateRepository certificateRepository;

    public CredentialContextFacadeImpl(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Override
    public List<CertificateSummary> getEvidenceCertificates(int ownerId) {
        return certificateRepository.findByOwnerId(ownerId).stream()
                .filter(certificate -> EVIDENCE_STATUSES.contains(certificate.getStatus()))
                .sorted(Comparator.comparing(Certificate::getId))
                .map(certificate -> new CertificateSummary(certificate.getId(), certificate.getCourseName(),
                        certificate.getInstitutionName()))
                .toList();
    }

    @Override
    public List<CertificateEvidence> getValidatedCertificates(int ownerId) {
        return certificateRepository.findByOwnerId(ownerId).stream()
                .filter(certificate -> certificate.getStatus() == VerificationStatus.VERIFIED)
                .sorted(Comparator.comparing(Certificate::getId))
                .map(CredentialContextFacadeImpl::toEvidence)
                .toList();
    }

    @Override
    public Optional<CertificateEvidence> getCertificate(int certificateId) {
        return certificateRepository.findById(certificateId).map(CredentialContextFacadeImpl::toEvidence);
    }

    private static CertificateEvidence toEvidence(Certificate certificate) {
        return new CertificateEvidence(certificate.getId(), certificate.getOwnerId(), certificate.getCourseName(),
                certificate.getInstitutionName(), certificate.getOcrText(),
                EVIDENCE_STATUSES.contains(certificate.getStatus()),
                certificate.getStatus() == VerificationStatus.VERIFIED);
    }
}
