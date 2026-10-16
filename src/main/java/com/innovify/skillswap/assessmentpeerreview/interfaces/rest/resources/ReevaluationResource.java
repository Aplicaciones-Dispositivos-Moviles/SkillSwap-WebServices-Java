package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A reevaluation ordered by a Verificador senior.
 *
 * @param id              the unique identifier of the reevaluation
 * @param sourceType      Attempt or Case
 * @param sourceId        the id of the attempt or of the case that is evaluated again
 * @param studentId       the student who demonstrates the skill again
 * @param orderedByUserId the senior who ordered it
 * @param justification   why the result was doubted
 * @param createdAt       when it was ordered (UTC)
 */
public record ReevaluationResource(int id, @Schema(allowableValues = {"Attempt", "Case"}) String sourceType,
                                   int sourceId, int studentId, int orderedByUserId, String justification,
                                   Instant createdAt) {
}
