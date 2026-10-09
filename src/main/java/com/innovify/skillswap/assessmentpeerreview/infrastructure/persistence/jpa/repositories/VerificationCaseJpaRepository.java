package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "verification_cases" table. Only {@link VerificationCaseRepositoryAdapter} uses it. */
public interface VerificationCaseJpaRepository extends JpaRepository<VerificationCase, Integer> {

    List<VerificationCase> findByVerifierUserIdOrderByIdDesc(int verifierUserId);

    Optional<VerificationCase> findFirstByStudentIdAndPathNodeIdAndStatusNot(int studentId, int pathNodeId,
                                                                           CaseStatus status);

    List<VerificationCase> findByStatusAndSkillTagOrderByIdAsc(CaseStatus status, String skillTag);

    List<VerificationCase> findByVerifierUserIdInAndStatusNot(Collection<Integer> verifierUserIds,
                                                              CaseStatus status);
}
