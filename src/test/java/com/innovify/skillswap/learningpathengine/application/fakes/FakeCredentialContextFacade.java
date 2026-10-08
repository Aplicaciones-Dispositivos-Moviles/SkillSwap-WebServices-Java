package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.credentialverification.application.acl.CertificateSummary;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Returns the configured certificates, oldest first, or fails when an exception is set. */
public class FakeCredentialContextFacade implements CredentialContextFacade {

    private final List<CertificateSummary> certificates = new ArrayList<>();
    private RuntimeException exceptionToThrow;

    public List<CertificateSummary> certificates() {
        return certificates;
    }

    public void failWith(RuntimeException exception) {
        this.exceptionToThrow = exception;
    }

    @Override
    public List<CertificateSummary> getEvidenceCertificates(int ownerId) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        return certificates.stream().sorted(Comparator.comparing(CertificateSummary::id)).toList();
    }
}
