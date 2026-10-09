package com.innovify.skillswap.subscriptionbilling.application.fakes;

import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGatewayException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Answers what each test sets for each student; a student without an answer has no purchase. */
public class FakePaymentGateway implements PaymentGateway {

    public static final String PRODUCT = "skillswap_premium_monthly:monthly";

    private final Map<Integer, PurchaseVerification> states = new ConcurrentHashMap<>();
    private final List<Integer> verified = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();
    private volatile PaymentGatewayException failure;

    /** An active purchase that renews, ending in 30 days. */
    public static PurchaseVerification active(String transaction) {
        return new PurchaseVerification(true, PRODUCT, Instant.now().plus(Duration.ofDays(30)), true, transaction,
                false);
    }

    public FakePaymentGateway willAnswer(int studentId, PurchaseVerification state) {
        states.put(studentId, state);
        return this;
    }

    /** Makes every following call throw, until it is called with null. */
    public void failWith(PaymentGatewayException failure) {
        this.failure = failure;
    }

    public void reset() {
        states.clear();
        verified.clear();
        cancelled.clear();
        failure = null;
    }

    /** The students whose purchase was verified, in order. */
    public List<Integer> verified() {
        return verified;
    }

    /** The store transactions whose renewals were cancelled, in order. */
    public List<String> cancelled() {
        return cancelled;
    }

    @Override
    public synchronized PurchaseVerification verifyPurchase(int studentId, String productId) {
        if (failure != null) {
            throw failure;
        }
        verified.add(studentId);
        return states.getOrDefault(studentId, PurchaseVerification.inactive());
    }

    @Override
    public synchronized void cancelRenewal(int studentId, String storeTransactionId) {
        if (failure != null) {
            throw failure;
        }
        cancelled.add(storeTransactionId);
        PurchaseVerification current = states.get(studentId);
        if (current != null && current.active()) {
            states.put(studentId, new PurchaseVerification(true, current.productId(), current.expiresAt(), false,
                    current.storeTransactionId(), current.sandbox()));
        }
    }
}
