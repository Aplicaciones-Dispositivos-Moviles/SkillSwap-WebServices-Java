package com.innovify.skillswap.credentialverification.application.acl;

import java.util.List;
import java.util.Optional;

/**
 * Anti-corruption facade through which other bounded contexts read Credential Verification data, without
 * depending on its aggregates or repositories.
 */
public interface CredentialContextFacade {

    /**
     * The certificates of a student that can support a skill as evidence: those that are neither suspicious
     * nor rejected. Ordered by id, so the oldest certificate comes first.
     */
    List<CertificateSummary> getEvidenceCertificates(int ownerId);

    /**
     * The certificates of a student that a verifier validated (Verified), which count as the skills they cover
     * already demonstrated. Ordered by id, so the oldest certificate comes first.
     */
    List<CertificateEvidence> getValidatedCertificates(int ownerId);

    /** A certificate, whatever its owner and status; empty when it does not exist. */
    Optional<CertificateEvidence> getCertificate(int certificateId);

    /** The data a verifier reviews to decide on a certificate; empty when it does not exist. */
    Optional<CertificateReviewView> getCertificateForReview(int certificateId);

    /**
     * Applies the decision of the verifier on a suspicious certificate: authentic moves it to Verified, otherwise
     * to Rejected. The reason is the observation of the reviewer, sent to the student with the push notification
     * of a rejection (ignored when authentic). It joins the transaction of the caller, if any, and never throws: a
     * failure is reported.
     */
    CertificateReviewOutcome resolveSuspiciousCertificate(int certificateId, boolean authentic, String reason);
}
