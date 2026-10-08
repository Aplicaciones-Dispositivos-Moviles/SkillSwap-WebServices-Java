package com.innovify.skillswap.credentialverification.application.acl;

import java.util.List;

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
}
