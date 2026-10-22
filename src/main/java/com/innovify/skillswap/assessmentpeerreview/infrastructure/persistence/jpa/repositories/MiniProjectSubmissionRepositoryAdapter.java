package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.EvaluationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link MiniProjectSubmissionRepository} port on top of Spring Data JPA. */
@Repository
public class MiniProjectSubmissionRepositoryAdapter implements MiniProjectSubmissionRepository {

    private final MiniProjectSubmissionJpaRepository jpaRepository;

    public MiniProjectSubmissionRepositoryAdapter(MiniProjectSubmissionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public MiniProjectSubmission save(MiniProjectSubmission submission) {
        return jpaRepository.saveAndFlush(submission);
    }

    @Override
    public Optional<MiniProjectSubmission> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<MiniProjectSubmission> findByIdForUpdate(int id) {
        return jpaRepository.findForUpdateById(id);
    }

    @Override
    public Optional<MiniProjectSubmission> findByMiniProjectId(int miniProjectId) {
        return jpaRepository.findByMiniProjectId(miniProjectId);
    }

    @Override
    public List<MiniProjectSubmission> findAwaitingEvaluation() {
        return jpaRepository.findByEvaluationStatusOrderBySubmittedAtAscIdAsc(EvaluationStatus.PENDING);
    }
}
