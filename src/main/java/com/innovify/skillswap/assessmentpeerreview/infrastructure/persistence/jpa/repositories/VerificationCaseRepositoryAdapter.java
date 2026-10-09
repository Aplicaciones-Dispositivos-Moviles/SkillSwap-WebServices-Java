package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link VerificationCaseRepository} port on top of Spring Data JPA. */
@Repository
public class VerificationCaseRepositoryAdapter implements VerificationCaseRepository {

    /** First key of the advisory locks on the escalations of a student, so they never collide with other locks. */
    static final int STUDENT_ESCALATIONS_LOCK_NAMESPACE = 1002;

    private final VerificationCaseJpaRepository jpaRepository;

    public VerificationCaseRepositoryAdapter(VerificationCaseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public VerificationCase save(VerificationCase verificationCase) {
        return jpaRepository.saveAndFlush(verificationCase);
    }

    @Override
    public Optional<VerificationCase> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<VerificationCase> findByVerifierUserId(int verifierUserId) {
        return jpaRepository.findByVerifierUserIdOrderByIdDesc(verifierUserId);
    }

    @Override
    public Optional<VerificationCase> findOpenByStudentAndNode(int studentId, int pathNodeId) {
        return jpaRepository.findFirstByStudentIdAndPathNodeIdAndStatusNot(studentId, pathNodeId,
                CaseStatus.RESOLVED);
    }

    @Override
    public List<VerificationCase> findPendingBySkillTag(String skillTag) {
        return jpaRepository.findByStatusAndSkillTagOrderByIdAsc(CaseStatus.PENDING, skillTag);
    }

    @Override
    public int countOpenedByStudentSince(int studentId, Instant since) {
        return Math.toIntExact(jpaRepository.countByStudentIdAndOpenedAtGreaterThanEqual(studentId, since));
    }

    @Override
    public void lockStudentEscalations(int studentId) {
        jpaRepository.lockStudent(STUDENT_ESCALATIONS_LOCK_NAMESPACE, studentId);
    }

    @Override
    public Map<Integer, Integer> countOpenByVerifierUserIds(Collection<Integer> verifierUserIds) {
        Set<Integer> ids = new HashSet<>(verifierUserIds);
        Map<Integer, Integer> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }

        // The unresolved cases of a handful of verifiers are few, so they are counted here.
        for (VerificationCase open : jpaRepository.findByVerifierUserIdInAndStatusNot(ids, CaseStatus.RESOLVED)) {
            counts.merge(open.getVerifierUserId(), 1, Integer::sum);
        }
        return counts;
    }
}
