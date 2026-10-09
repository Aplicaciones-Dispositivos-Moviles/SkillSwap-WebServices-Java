package com.innovify.skillswap.credentialverification.interfaces.rest.transform;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.interfaces.rest.resources.CertificateResource;

public final class CertificateResourceFromEntityAssembler {

    private CertificateResourceFromEntityAssembler() {
    }

    public static CertificateResource toResourceFromEntity(Certificate entity, String fileUrl) {
        return new CertificateResource(
                entity.getId(),
                entity.getOwnerId(),
                entity.getHolderName(),
                entity.getInstitutionName(),
                entity.getCourseName(),
                entity.getIssueDate(),
                entity.getDurationHours(),
                entity.getCertificateNumber(),
                entity.getVerificationCode(),
                entity.getVerificationUrl(),
                entity.getStatus().value(),
                entity.getVerificationMethod().value(),
                entity.getRiskAssessment() == null ? null : entity.getRiskAssessment().level().value(),
                entity.getCreatedAt(),
                entity.getVerifiedAt(),
                fileUrl,
                entity.hasHolderNameMismatch());
    }
}
