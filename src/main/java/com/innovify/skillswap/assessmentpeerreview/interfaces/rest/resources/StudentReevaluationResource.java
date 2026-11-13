package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A reevaluation ordered on a result of the student who asks. It has nothing about the senior who ordered it.
 *
 * @param id            the unique identifier of the reevaluation
 * @param sourceType    Attempt (a passed quiz attempt) or Case (an approved verification case)
 * @param sourceId      the id of the attempt or of the case that is evaluated again
 * @param pathNodeId    the node of the learning path that was reopened; null when the result can no longer be found
 * @param skillTag      the skill to demonstrate again; null when the result can no longer be found
 * @param justification why the result was doubted
 * @param createdAt     when it was ordered (UTC)
 */
public record StudentReevaluationResource(int id, @Schema(allowableValues = {"Attempt", "Case"}) String sourceType,
                                          int sourceId, @Schema(nullable = true) Integer pathNodeId,
                                          @Schema(nullable = true) String skillTag, String justification,
                                          Instant createdAt) {
}
