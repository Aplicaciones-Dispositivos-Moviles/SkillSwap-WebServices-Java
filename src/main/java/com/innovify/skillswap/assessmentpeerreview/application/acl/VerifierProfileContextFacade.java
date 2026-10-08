package com.innovify.skillswap.assessmentpeerreview.application.acl;

/**
 * Anti-corruption facade through which other bounded contexts (Reputation) act on verifier profiles, without
 * depending on their aggregates or repositories.
 */
public interface VerifierProfileContextFacade {

    /**
     * Stores the average rating of the verifier. The review count is not touched.
     *
     * @return false when the user has no verifier profile; true once the rating is stored
     * @throws com.innovify.skillswap.shared.domain.exceptions.DomainException when the rating is not valid
     */
    boolean updateRating(int verifierUserId, double rating);
}
