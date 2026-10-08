package com.innovify.skillswap.assessmentpeerreview.application.acl;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import java.util.Optional;

/** Facade implementation over the verifier profile repository. */
public class VerifierProfileContextFacadeImpl implements VerifierProfileContextFacade {

    private final VerifierProfileRepository profileRepository;

    public VerifierProfileContextFacadeImpl(VerifierProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public boolean updateRating(int verifierUserId, double rating) {
        Optional<VerifierProfile> found = profileRepository.findByUserId(verifierUserId);
        if (found.isEmpty()) {
            return false;
        }

        // A revoked profile keeps receiving its rating: the history stays accurate if it is restored.
        profileRepository.save(found.get().updateRating(rating));
        return true;
    }
}
