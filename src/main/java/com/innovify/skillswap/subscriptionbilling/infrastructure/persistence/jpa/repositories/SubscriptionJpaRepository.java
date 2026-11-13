package com.innovify.skillswap.subscriptionbilling.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Spring Data access to the "subscriptions" table. Only {@link SubscriptionRepositoryAdapter} uses it. */
public interface SubscriptionJpaRepository extends JpaRepository<Subscription, Integer> {

    Optional<Subscription> findFirstByStudentIdAndStatusNot(int studentId, SubscriptionStatus status);

    /** SELECT ... FOR UPDATE: it waits for the other transactions that hold the row. Needs a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Subscription> findForUpdateFirstByStudentIdAndStatusNot(int studentId, SubscriptionStatus status);

    List<Subscription> findByStatusNotAndCurrentPeriodEndLessThanEqualOrderByCurrentPeriodEndAsc(
            SubscriptionStatus status, Instant moment);
}
