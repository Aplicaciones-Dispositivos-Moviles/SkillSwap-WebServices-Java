package com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A subscription to the monthly plan.
 *
 * @param id                 the subscription
 * @param studentId          the student who owns it
 * @param planName           the commercial name of the plan
 * @param productId          the store product
 * @param price              the price of each period, VAT (IGV) included
 * @param currency           the currency of the price (PEN)
 * @param status             Active, Cancelled (the plan is kept until currentPeriodEnd) or Expired
 * @param storeTransactionId the Google Play transaction reported by RevenueCat, if any
 * @param startedAt          when it was activated (UTC)
 * @param currentPeriodEnd   when the period already paid ends (UTC): the next renewal, or the end of the plan
 * @param cancelledAt        when it was cancelled (UTC), if it was
 * @param expiredAt          when it expired (UTC), if it did
 */
public record SubscriptionResource(int id, int studentId, String planName, String productId, BigDecimal price,
                                   String currency,
                                   @Schema(allowableValues = {"Active", "Cancelled", "Expired"}) String status,
                                   String storeTransactionId, Instant startedAt, Instant currentPeriodEnd,
                                   Instant cancelledAt, Instant expiredAt) {
}
