package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to the "mini_project_submissions" table. Only {@link MiniProjectSubmissionRepositoryAdapter} uses it. */
public interface MiniProjectSubmissionJpaRepository extends JpaRepository<MiniProjectSubmission, Integer> {

    Optional<MiniProjectSubmission> findByMiniProjectId(int miniProjectId);

    /** SELECT ... FOR UPDATE: it waits for the other transactions that hold the delivery. Needs a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MiniProjectSubmission s where s.id = :id")
    Optional<MiniProjectSubmission> findForUpdateById(@Param("id") int id);
}
