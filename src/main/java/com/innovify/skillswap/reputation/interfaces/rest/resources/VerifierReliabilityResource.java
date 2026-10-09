package com.innovify.skillswap.reputation.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
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
 * @param rank                     Bronze (0 to 29 resolved cases), Silver (30 to 99) or Gold (100 or more)
 * @param seniorVerifier           whether the verifier is a Verificador senior: Gold rank and a score of 90 or
 *                                 more
 */
public record VerifierReliabilityResource(int id, int verifierUserId, int resolvedCasesCount,
                                          int overturnedDecisionsCount, int sanctionsCount, int score,
                                          Instant updatedAt,
                                          @Schema(allowableValues = {"Bronze", "Silver", "Gold"}) String rank,
                                          boolean seniorVerifier) {
}
