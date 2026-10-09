package com.innovify.skillswap.assessmentpeerreview.application.acl;

import java.util.List;

/**
 * Anti-corruption facade through which other bounded contexts (Reputation, Moderation &amp; Disputes) read and act
 * on verifier profiles, without depending on their aggregates or repositories.
 */
public interface VerifierProfileContextFacade {

    /**
     * Stores the average rating of the verifier. The review count is not touched.
     *
     * @return false when the user has no verifier profile; true once the rating is stored
     * @throws com.innovify.skillswap.shared.domain.exceptions.DomainException when the rating is not valid
     */
    boolean updateRating(int verifierUserId, double rating);

    /** Whether the user has a verifier profile that was not revoked. */
    boolean isEnabledVerifier(int userId);

    /** The enabled verifiers who are available right now, with their workload, ordered by user id. */
    List<VerifierWorkload> getAvailableVerifiers();
}
