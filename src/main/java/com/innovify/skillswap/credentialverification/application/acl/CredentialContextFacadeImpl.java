package com.innovify.skillswap.credentialverification.application.acl;

import com.innovify.skillswap.credentialverification.application.commandservices.CertificateCommandService;
import com.innovify.skillswap.credentialverification.application.queryservices.CertificateQueryService;
import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
    public CertificateReviewOutcome resolveSuspiciousCertificate(int certificateId, boolean authentic,
                                                                 String reason) {
        var result = commandService.handle(
                new ResolveCertificateDisputeCommand(certificateId, authentic, abbreviate(reason)));
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

    /**
     * The reason of a rejection goes into a push notification, so it is cut to what a rejection accepts: the notes
     * of the reviewer can be longer, and the whole text stays with whoever recorded it.
     */
    private static String abbreviate(String reason) {
        if (reason == null) {
            return null;
        }
        String stripped = reason.strip();
        int max = ResolveCertificateDisputeCommand.MAX_REASON_LENGTH;
        return stripped.length() <= max ? stripped : stripped.substring(0, max - 1).stripTrailing() + "\u2026";
    }

    /** The link is only a convenience for the reviewer: when it cannot be signed the rest is still shown. */
    private String fileUrlOf(Certificate certificate) {
        try {
            return queryService.getFileUrl(certificate);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public List<CertificationDemandView> getMostFrequentCertifications(Instant from, Instant to, int limit) {
        if (from == null || to == null || !from.isBefore(to) || limit < 1) {
            return List.of();
        }

        // Key: the name without letter case and extra spaces. Value: how it was first written, and how many.
        Map<String, String> displayNames = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        certificateRepository.findCreatedBetween(from, to).stream()
                .filter(certificate -> certificate.getStatus() != VerificationStatus.REJECTED)
                .filter(certificate -> certificate.getCourseName() != null && !certificate.getCourseName().isBlank())
                .sorted(Comparator.comparing(Certificate::getId))
                .forEach(certificate -> {
                    String display = certificate.getCourseName().strip().replaceAll("\\s+", " ");
                    String key = display.toLowerCase(Locale.ROOT);
                    displayNames.putIfAbsent(key, display);
                    counts.merge(key, 1, Integer::sum);
                });
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new CertificationDemandView(displayNames.get(entry.getKey()), entry.getValue()))
                .toList();
    }
}
