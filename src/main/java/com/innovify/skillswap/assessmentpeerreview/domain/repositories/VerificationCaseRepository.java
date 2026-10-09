package com.innovify.skillswap.assessmentpeerreview.domain.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port of the {@link VerificationCase} aggregate. */
public interface VerificationCaseRepository {

    /** Persists a new or updated case and flushes right away. */
    VerificationCase save(VerificationCase verificationCase);

    Optional<VerificationCase> findById(int id);

    /** The cases assigned to the verifier, newest first. */
    List<VerificationCase> findByVerifierUserId(int verifierUserId);

    /** The unresolved case of the student for the node, if any. */
    Optional<VerificationCase> findOpenByStudentAndNode(int studentId, int pathNodeId);

    /** The cases of the skill that still wait for a verifier, oldest first. */
    List<VerificationCase> findPendingBySkillTag(String skillTag);

    /** How many cases the student opened from that moment on, whatever their state (appeals do not open cases). */
    int countOpenedByStudentSince(int studentId, Instant since);

    /**
     * Serializes the escalations of a student until the current transaction ends, so two attempts cannot both see
     * room under the monthly quota of the plan. It must run inside a transaction.
     */
    void lockStudentEscalations(int studentId);

    /** The number of unresolved cases of each verifier; verifiers without any are not in the map. */
    Map<Integer, Integer> countOpenByVerifierUserIds(Collection<Integer> verifierUserIds);
}
