package com.innovify.skillswap.assessmentpeerreview.application.internal;

import java.util.Collection;
import java.util.Optional;

/** Chooses the verifier who takes the final demonstration of a path. */
public interface DemonstrationAssignmentService {

    /**
     * The available verifier enabled in any of the skills with the least work (unresolved cases plus active
     * demonstrations); ties go to the lowest user id. The student is never chosen. Empty when nobody qualifies.
     */
    Optional<DemonstrationAssignment> findVerifier(int studentId, Collection<String> skillTags);
}
