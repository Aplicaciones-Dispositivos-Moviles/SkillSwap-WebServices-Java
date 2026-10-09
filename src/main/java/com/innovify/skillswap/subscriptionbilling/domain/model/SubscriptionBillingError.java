package com.innovify.skillswap.subscriptionbilling.domain.model;

/** Errors of the Subscription &amp; Billing context. The API code is the PascalCase name (PurchaseNotVerified...). */
public enum SubscriptionBillingError {
    NONE,
    INVALID_PRODUCT,
    INVALID_WEBHOOK_EVENT,
    INVALID_WEBHOOK_AUTHORIZATION,
    PURCHASE_NOT_VERIFIED,
    SUBSCRIPTION_NOT_FOUND,
    NOT_SUBSCRIPTION_OWNER,
    SUBSCRIPTION_NOT_ACTIVE,
    PAYMENT_GATEWAY_UNAVAILABLE,
    OPERATION_CANCELLED,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}
