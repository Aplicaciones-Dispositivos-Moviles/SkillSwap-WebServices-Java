package com.innovify.skillswap.moderationdisputes.interfaces.rest.transform;

import com.innovify.skillswap.credentialverification.application.acl.CertificateReviewView;
import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.resources.CertificateEvidenceResource;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.resources.DisputeEvidenceResource;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.resources.DisputeResource;

/** Converts the Moderation &amp; Disputes model into REST resources. */
public final class DisputeResourceAssemblers {

    private DisputeResourceAssemblers() {
    }

    public static DisputeResource toResource(Dispute dispute) {
        return new DisputeResource(dispute.getId(), dispute.getSourceType().value(), dispute.getSourceReferenceId(),
                dispute.getReason(), dispute.getStatus().value(),
                dispute.getOutcome() == null ? null : dispute.getOutcome().value(), dispute.getCoordinatorNotes(),
                dispute.getAssignedVerifierUserId(), dispute.isAssignedToSenior(), dispute.getAssignedAt(),
                dispute.getRaisedAt(), dispute.getResolvedAt());
    }

    public static DisputeEvidenceResource toResource(Dispute dispute, CertificateReviewView certificate) {
        return new DisputeEvidenceResource(toResource(dispute),
                certificate == null ? null : new CertificateEvidenceResource(certificate.id(),
                        certificate.holderName(), certificate.institutionName(), certificate.courseName(),
                        certificate.issueDate(), certificate.certificateNumber(), certificate.verificationCode(),
                        certificate.verificationUrl(), certificate.status(), certificate.riskLevel(),
                        certificate.holderNameMismatch(), certificate.createdAt(), certificate.fileUrl()));
    }
}
