package com.innovify.skillswap.subscriptionbilling.application.commandservices;

import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.subscriptionbilling.domain.model.aggregates.Subscription;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CancelSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CreateSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ExpireSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ProcessRevenueCatEventCommand;

/**
 * Subscription command service interface. The state of a subscription always comes from the payment gateway; the
 * client and the webhook payloads only say when to read it.
 */
public interface SubscriptionCommandService {

    /**
     * Verifies the purchase with the gateway and activates the subscription, or brings the current one up to
     * date when it already exists. Fails with INVALID_PRODUCT, PURCHASE_NOT_VERIFIED or
     * PAYMENT_GATEWAY_UNAVAILABLE.
     */
    Result<Subscription> handle(CreateSubscriptionCommand command);

    /**
     * Asks the store to stop the renewals and marks the subscription cancelled; the plan is kept until the end of
     * the paid period. Cancelling twice changes nothing. Fails with SUBSCRIPTION_NOT_FOUND,
     * NOT_SUBSCRIPTION_OWNER, SUBSCRIPTION_NOT_ACTIVE or PAYMENT_GATEWAY_UNAVAILABLE.
     */
    Result<Subscription> handle(CancelSubscriptionCommand command);

    /**
     * Renews or expires a subscription whose paid period ended, according to the gateway. An expired subscription
     * publishes {@code SubscriptionExpired}. Fails with SUBSCRIPTION_NOT_FOUND or PAYMENT_GATEWAY_UNAVAILABLE.
     */
    Result<Subscription> handle(ExpireSubscriptionCommand command);

    /**
     * Applies a webhook notification, once per event id: TEST events are only acknowledged, and for the others the
     * state of the student is read again from the gateway (activation, renewal, cancellation or expiration). Fails
     * with INVALID_WEBHOOK_EVENT or PAYMENT_GATEWAY_UNAVAILABLE, so the gateway retries.
     */
    Result<WebhookEventOutcome> handle(ProcessRevenueCatEventCommand command);
}
