package com.innovify.skillswap.reputation.interfaces.rest.resources;

import java.time.Instant;

/**
 * The reliability of a verifier.
 *
 * @param id                       the reliability
 * @param verifierUserId           the verifier
 * @param resolvedCasesCount       the cases they resolved
 * @param overturnedDecisionsCount their decisions that were overturned after an appeal
 * @param sanctionsCount           the sanctions applied to them
 * @param score                    from 0 to 100
 * @param updatedAt                when it last changed (UTC)
 */
public record VerifierReliabilityResource(int id, int verifierUserId, int resolvedCasesCount,
                                          int overturnedDecisionsCount, int sanctionsCount, int score,
                                          Instant updatedAt) {
}
