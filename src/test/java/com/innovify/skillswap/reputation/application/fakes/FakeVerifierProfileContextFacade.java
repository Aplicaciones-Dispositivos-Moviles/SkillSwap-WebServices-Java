package com.innovify.skillswap.reputation.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierWorkload;
import java.util.ArrayList;
import java.util.List;

/** Records the ratings it is asked to store. */
public class FakeVerifierProfileContextFacade implements VerifierProfileContextFacade {

    /** A rating update: the verifier and the rating. */
    public record Update(int verifierUserId, double rating) {
    }

    private final List<Update> updates = new ArrayList<>();
    private boolean updated = true;
    private RuntimeException failure;

    public List<Update> updates() {
        return updates;
    }

    /** What the facade answers: false stands for a user without a verifier profile. */
    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    /** Makes every following call throw the given exception. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public boolean updateRating(int verifierUserId, double rating) {
        if (failure != null) {
            throw failure;
        }
        updates.add(new Update(verifierUserId, rating));
        return updated;
    }

    @Override
    public boolean isEnabledVerifier(int userId) {
        return false;
    }

    @Override
    public List<VerifierWorkload> getAvailableVerifiers() {
        return List.of();
    }
}
