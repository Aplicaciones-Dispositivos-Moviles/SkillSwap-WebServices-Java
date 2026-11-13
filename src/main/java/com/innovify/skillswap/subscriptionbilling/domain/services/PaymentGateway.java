package com.innovify.skillswap.subscriptionbilling.domain.services;

import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;

/**
 * Contract with the external gateway that knows the real state of the purchases (Google Play Billing, through
 * RevenueCat), so the gateway can be replaced or simulated without changing the domain or the application.
 */
public interface PaymentGateway {

    /**
     * The real state of the paid plan of the student: active or not, when the period ends and whether it renews.
     *
     * @param studentId the student, which is the app user id in the gateway
     * @param productId the product that must grant the plan, or null to accept any that grants it
     * @throws PaymentGatewayException when the gateway cannot answer
     */
    PurchaseVerification verifyPurchase(int studentId, String productId);

    /**
     * Asks the store to stop renewing the subscription. The plan is kept until the end of the period.
     *
     * @param storeTransactionId the store transaction of the subscription
     * @throws PaymentGatewayException when the gateway cannot do it
     */
    void cancelRenewal(int studentId, String storeTransactionId);
}
