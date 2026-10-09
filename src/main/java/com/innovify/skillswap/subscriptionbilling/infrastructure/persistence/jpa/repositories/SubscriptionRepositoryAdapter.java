package com.innovify.skillswap.subscriptionbilling.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.SubscriptionStatus;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link SubscriptionRepository} port on top of Spring Data JPA. */
@Repository
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpaRepository;

    public SubscriptionRepositoryAdapter(SubscriptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Subscription save(Subscription subscription) {
        return jpaRepository.saveAndFlush(subscription);
    }

    @Override
    public Optional<Subscription> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Subscription> findCurrentByStudentId(int studentId) {
        return jpaRepository.findFirstByStudentIdAndStatusNot(studentId, SubscriptionStatus.EXPIRED);
    }

    @Override
    public Optional<Subscription> findCurrentByStudentIdForUpdate(int studentId) {
        return jpaRepository.findForUpdateFirstByStudentIdAndStatusNot(studentId, SubscriptionStatus.EXPIRED);
    }

    @Override
    public List<Subscription> findDueForExpiration(Instant moment) {
        return jpaRepository.findByStatusNotAndCurrentPeriodEndLessThanEqualOrderByCurrentPeriodEndAsc(
                SubscriptionStatus.EXPIRED, moment);
    }
}
