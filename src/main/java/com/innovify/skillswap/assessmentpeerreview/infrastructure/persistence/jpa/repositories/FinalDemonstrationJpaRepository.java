package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to the "final_demonstrations" table. Only {@link FinalDemonstrationRepositoryAdapter} uses it. */
public interface FinalDemonstrationJpaRepository extends JpaRepository<FinalDemonstration, Integer> {

    /** SELECT ... FOR UPDATE: it waits for the other transactions that hold the demonstration. Needs a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from FinalDemonstration d where d.id = :id")
    Optional<FinalDemonstration> findForUpdateById(@Param("id") int id);

    List<FinalDemonstration> findByLearningPathIdAndStatusIn(int learningPathId,
                                                              Collection<DemonstrationStatus> statuses);

    List<FinalDemonstration> findByStudentIdOrderByCreatedAtDescIdDesc(int studentId);

    List<FinalDemonstration> findByVerifierUserIdAndStatusNotOrderByScheduledAtAscIdAsc(int verifierUserId,
                                                                                         DemonstrationStatus status);

    List<FinalDemonstration> findByVerifierUserIdInAndStatusIn(Collection<Integer> verifierUserIds,
                                                                Collection<DemonstrationStatus> statuses);
}
