package com.innovify.skillswap.moderationdisputes.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to the "disputes" table. Only {@link DisputeRepositoryAdapter} uses it. */
public interface DisputeJpaRepository extends JpaRepository<Dispute, Integer> {

    /** SELECT ... FOR UPDATE: it waits for the other transactions that hold the dispute. Needs a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findForUpdateById(@Param("id") int id);

    Optional<Dispute> findFirstBySourceTypeAndSourceReferenceId(DisputeSourceType sourceType, int sourceReferenceId);

    List<Dispute> findByAssignedVerifierUserIdOrderByIdAsc(int verifierUserId);

    List<Dispute> findByAssignedVerifierUserIdAndStatusOrderByIdAsc(int verifierUserId, DisputeStatus status);

    @Query("select d.id from Dispute d where d.status = :status and d.assignedVerifierUserId is null order by d.id")
    List<Integer> findUnassignedIdsByStatus(@Param("status") DisputeStatus status);

    List<Dispute> findByAssignedVerifierUserIdInAndStatus(Collection<Integer> verifierUserIds, DisputeStatus status);
}
