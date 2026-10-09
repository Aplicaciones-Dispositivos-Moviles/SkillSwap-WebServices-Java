package com.innovify.skillswap.credentialverification.application.acl;

import com.innovify.skillswap.credentialverification.application.commandservices.CertificateCommandService;
import com.innovify.skillswap.credentialverification.application.queryservices.CertificateQueryService;
import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
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
    private final CertificateCommandService commandService;
    private final CertificateQueryService queryService;

    public CredentialContextFacadeImpl(CertificateRepository certificateRepository,
                                       CertificateCommandService commandService,
                                       CertificateQueryService queryService) {
        this.certificateRepository = certificateRepository;
        this.commandService = commandService;
        this.queryService = queryService;
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
    public Optional<CertificateReviewView> getCertificateForReview(int certificateId) {
        return certificateRepository.findById(certificateId).map(certificate -> new CertificateReviewView(
                certificate.getId(), certificate.getOwnerId(), certificate.getHolderName(),
                certificate.getInstitutionName(), certificate.getCourseName(), certificate.getIssueDate(),
                certificate.getCertificateNumber(), certificate.getVerificationCode(),
                certificate.getVerificationUrl(), certificate.getStatus().value(),
                certificate.getRiskAssessment() == null ? null : certificate.getRiskAssessment().level().value(),
                certificate.hasHolderNameMismatch(), certificate.getCreatedAt(), fileUrlOf(certificate)));
    }

    @Override
    public CertificateReviewOutcome resolveSuspiciousCertificate(int certificateId, boolean authentic) {
        var result = commandService.handle(new ResolveCertificateDisputeCommand(certificateId, authentic));
        if (result.isSuccess()) {
            return CertificateReviewOutcome.RESOLVED;
        }
        if (result.error() == CredentialVerificationError.CERTIFICATE_NOT_FOUND) {
            return CertificateReviewOutcome.NOT_FOUND;
        }
        return result.error() == CredentialVerificationError.INVALID_STATUS_TRANSITION
                ? CertificateReviewOutcome.NOT_SUSPICIOUS
                : CertificateReviewOutcome.FAILED;
    }

    /** The link is only a convenience for the reviewer: when it cannot be signed the rest is still shown. */
    private String fileUrlOf(Certificate certificate) {
        try {
            return queryService.getFileUrl(certificate);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
