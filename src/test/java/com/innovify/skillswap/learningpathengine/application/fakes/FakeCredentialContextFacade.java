package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.credentialverification.application.acl.CertificateEvidence;
import com.innovify.skillswap.credentialverification.application.acl.CertificateSummary;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Returns the configured certificates, oldest first, or fails when an exception is set. The summaries are the
 * supporting evidence ({@link #certificates()}); the detailed certificates ({@link #details()}) are the ones
 * looked up one by one and, when validated, recognized as demonstrated skills.
 */
public class FakeCredentialContextFacade implements CredentialContextFacade {

    private final List<CertificateSummary> certificates = new ArrayList<>();
    private final List<CertificateEvidence> details = new ArrayList<>();
    private RuntimeException exceptionToThrow;

    public List<CertificateSummary> certificates() {
        return certificates;
    }

    public List<CertificateEvidence> details() {
        return details;
    }

    /** A certificate that passed the risk check (Unverified): supporting evidence only. */
    public CertificateEvidence addUnverified(int id, int ownerId, String courseName, String ocrText) {
        CertificateEvidence evidence = new CertificateEvidence(id, ownerId, courseName, null, ocrText, true, false);
        details.add(evidence);
        return evidence;
    }

    /** A certificate a verifier validated (Verified). */
    public CertificateEvidence addValidated(int id, int ownerId, String courseName, String ocrText) {
        CertificateEvidence evidence = new CertificateEvidence(id, ownerId, courseName, null, ocrText, true, true);
        details.add(evidence);
        return evidence;
    }

    /** A pending, suspicious or rejected certificate: not evidence. */
    public CertificateEvidence addNotEvidence(int id, int ownerId, String courseName) {
        CertificateEvidence evidence = new CertificateEvidence(id, ownerId, courseName, null, "", false, false);
        details.add(evidence);
        return evidence;
    }

    public void failWith(RuntimeException exception) {
        this.exceptionToThrow = exception;
    }

    @Override
    public List<CertificateSummary> getEvidenceCertificates(int ownerId) {
        failIfConfigured();
        return certificates.stream().sorted(Comparator.comparing(CertificateSummary::id)).toList();
    }

    @Override
    public List<CertificateEvidence> getValidatedCertificates(int ownerId) {
        failIfConfigured();
        return details.stream()
                .filter(certificate -> certificate.ownerId() == ownerId && certificate.validated())
                .sorted(Comparator.comparing(CertificateEvidence::id))
                .toList();
    }

    @Override
    public Optional<CertificateEvidence> getCertificate(int certificateId) {
        failIfConfigured();
        return details.stream().filter(certificate -> certificate.id() == certificateId).findFirst();
    }

    private void failIfConfigured() {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
    }
}
