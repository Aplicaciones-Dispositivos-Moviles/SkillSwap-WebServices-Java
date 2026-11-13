package com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * The plan a student pays for. The free plan is not a subscription: a student without a current one is on it.
 *
 * @param name      commercial name of the plan
 * @param productId the subscription product configured in Google Play Console, as RevenueCat reports it
 * @param price     the price of each period, VAT (IGV) included
 */
public record SubscriptionPlan(String name, String productId, Money price) {

    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_PRODUCT_ID_LENGTH = 100;

    public static final String MONTHLY_NAME = "Plan Mensual";
    /** S/ 29.90 a month, VAT (IGV) included. */
    public static final Money MONTHLY_PRICE = Money.soles("29.90");
    /** The product used when the store does not report one (the simulated gateway). */
    public static final String DEFAULT_MONTHLY_PRODUCT_ID = "skillswap_premium_monthly";

    public SubscriptionPlan {
        String text = name == null ? "" : name.strip();
        if (text.isEmpty() || text.length() > MAX_NAME_LENGTH) {
            throw new DomainException("The plan name must have between 1 and %d characters."
                    .formatted(MAX_NAME_LENGTH));
        }
        String product = productId == null ? "" : productId.strip();
        if (product.isEmpty() || product.length() > MAX_PRODUCT_ID_LENGTH) {
            throw new DomainException("The product id must have between 1 and %d characters."
                    .formatted(MAX_PRODUCT_ID_LENGTH));
        }
        if (price == null) {
            throw new DomainException("The plan needs a price.");
        }

        name = text;
        productId = product;
    }

    /** The monthly plan, the only paid one, sold as the given store product. */
    public static SubscriptionPlan monthly(String productId) {
        String product = productId == null || productId.isBlank() ? DEFAULT_MONTHLY_PRODUCT_ID : productId;
        return new SubscriptionPlan(MONTHLY_NAME, product, MONTHLY_PRICE);
    }
}
