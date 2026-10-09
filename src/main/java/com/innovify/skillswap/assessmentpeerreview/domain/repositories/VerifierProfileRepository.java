package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import java.util.List;
import java.util.Optional;

/** Persistence port of the {@link VerifierProfile} aggregate. */
public interface VerifierProfileRepository {

    /** Persists a new or updated profile and flushes right away. */
    VerifierProfile save(VerifierProfile profile);

    Optional<VerifierProfile> findByUserId(int userId);

    /** The still-enabled (not revoked) profiles that can review the skill. */
    List<VerifierProfile> findEnabledBySkillTag(String skillTag);

    /** Every still-enabled (not revoked) profile, whatever its skills. */
    List<VerifierProfile> findEnabled();
}
