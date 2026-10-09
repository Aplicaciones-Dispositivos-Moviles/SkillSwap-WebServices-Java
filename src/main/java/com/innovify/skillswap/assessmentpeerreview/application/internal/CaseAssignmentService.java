package com.innovify.skillswap.assessmentpeerreview.application.internal;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import java.util.Optional;

/** Gives pending verification cases to the least loaded qualified verifier. */
public interface CaseAssignmentService {

    /**
     * Assigns the case when a verifier qualifies. The case is not saved: the caller does it.
     *
     * @return true when the case was assigned
     */
    boolean tryAssign(VerificationCase verificationCase);

    /**
     * Assigns, oldest first, the pending cases of the given skills that now have a verifier. Each assigned
     * case is saved.
     *
     * @return the number of cases assigned
     */
    int assignPending(Iterable<String> skillTags);

    /**
     * The least loaded qualified verifier who could take an assigned case instead of its current verifier: never its
     * student, its current verifier, nor the one who resolved it before an appeal. Nothing is changed.
     */
    Optional<Integer> findReplacementVerifier(VerificationCase verificationCase);
}
