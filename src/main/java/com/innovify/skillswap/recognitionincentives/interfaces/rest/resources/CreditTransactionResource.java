package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

import java.time.Instant;

/**
 * A movement of a wallet.
 *
 * @param id            the movement
 * @param walletId      the wallet it belongs to
 * @param amount        the SkillCredits of the movement, always positive
 * @param type          Earned (credits gained by resolving a case) or Redeemed (credits spent on a benefit)
 * @param description   what the movement was for
 * @param relatedCaseId the verification case that paid an earned movement, if any
 * @param createdAt     when it was recorded (UTC)
 * @param redemptionItem the benefit a redeemed movement bought (AdvancedPathUnlock or ContributionCertificate);
 *                       null for the earned ones
 */
public record CreditTransactionResource(int id, int walletId, int amount, String type, String description,
                                        Integer relatedCaseId, Instant createdAt, String redemptionItem) {
}
