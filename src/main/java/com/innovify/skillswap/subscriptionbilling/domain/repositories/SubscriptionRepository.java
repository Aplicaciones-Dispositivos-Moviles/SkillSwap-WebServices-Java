package com.innovify.skillswap.subscriptionbilling.domain.repositories;

import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port of the {@link Subscription} aggregate. */
public interface SubscriptionRepository {

    /** Persists a new or updated subscription and flushes right away. */
    Subscription save(Subscription subscription);

    Optional<Subscription> findById(int id);

    /** The subscription of the student that has not expired; a student has at most one. */
    Optional<Subscription> findCurrentByStudentId(int studentId);

    /**
     * Same as {@link #findCurrentByStudentId(int)} but locks the row until the current transaction ends, so a
     * webhook and a request of the app cannot change the same subscription at once. It must run inside a
     * transaction.
     */
    Optional<Subscription> findCurrentByStudentIdForUpdate(int studentId);

    /** The subscriptions that have not expired although their paid period ended before the moment. */
    List<Subscription> findDueForExpiration(Instant moment);
}
