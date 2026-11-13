package com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects;

import java.time.Instant;

/**
 * The state of the paid entitlement of a student, as the payment gateway reports it. The backend never trusts
 * the client about a purchase: this is the only source of truth.
 *
 * @param active             whether the entitlement grants the paid plan right now (grace period included)
 * @param productId          the store product that grants it; null when it is not active
 * @param expiresAt          when the paid period (or its grace period) ends; null when it is not active
 * @param willRenew          whether the store will renew it at the end of the period (false once cancelled)
 * @param storeTransactionId the store transaction of the purchase (Google Play order id), if reported
 * @param sandbox            whether it is a test purchase
 */
public record PurchaseVerification(boolean active, String productId, Instant expiresAt, boolean willRenew,
                                   String storeTransactionId, boolean sandbox) {

    public PurchaseVerification {
        if (active && expiresAt == null) {
            throw new IllegalArgumentException("An active entitlement needs its expiration.");
        }
    }

    /** No paid entitlement: the student is on the free plan. */
    public static PurchaseVerification inactive() {
        return new PurchaseVerification(false, null, null, false, null, false);
    }
}
