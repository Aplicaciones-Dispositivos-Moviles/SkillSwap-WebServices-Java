package com.innovify.skillswap.subscriptionbilling.application.fakes;

import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.SubscriptionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * In-memory repository that assigns ids like the database does and, like its partial unique index, refuses a
 * second subscription that has not expired for the same student.
 */
public class FakeSubscriptionRepository implements SubscriptionRepository {

    private final List<Subscription> items = new ArrayList<>();
    private int nextId = 1;
    private int lockedReads;
    private RuntimeException saveFailure;
    private Runnable beforeNextInsert;

    public List<Subscription> items() {
        return items;
    }

    /** How many times a subscription was read with a lock. */
    public int lockedReads() {
        return lockedReads;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    /** Runs the action right before the next new subscription is inserted, to simulate a concurrent request. */
    public void beforeNextInsert(Runnable action) {
        this.beforeNextInsert = action;
    }

    @Override
    public Subscription save(Subscription subscription) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (subscription.getId() == null) {
            if (beforeNextInsert != null) {
                Runnable action = beforeNextInsert;
                beforeNextInsert = null;
                action.run();
            }
            boolean conflict = !subscription.isExpired() && items.stream().anyMatch(existing ->
                    existing.getStudentId() == subscription.getStudentId() && !existing.isExpired());
            if (conflict) {
                throw new DataIntegrityViolationException("ux_subscriptions_one_current_per_student");
            }
            ReflectionTestUtils.setField(subscription, "id", nextId++);
            items.add(subscription);
        }
        return subscription;
    }

    @Override
    public Optional<Subscription> findById(int id) {
        return items.stream().filter(s -> s.getId() == id).findFirst();
    }

    @Override
    public Optional<Subscription> findCurrentByStudentId(int studentId) {
        return items.stream().filter(s -> s.getStudentId() == studentId && !s.isExpired()).findFirst();
    }

    @Override
    public Optional<Subscription> findCurrentByStudentIdForUpdate(int studentId) {
        lockedReads++;
        return findCurrentByStudentId(studentId);
    }

    @Override
    public List<Subscription> findDueForExpiration(Instant moment) {
        return items.stream()
                .filter(s -> !s.isExpired() && !s.getCurrentPeriodEnd().isAfter(moment))
                .sorted(Comparator.comparing(Subscription::getCurrentPeriodEnd))
                .toList();
    }
}
