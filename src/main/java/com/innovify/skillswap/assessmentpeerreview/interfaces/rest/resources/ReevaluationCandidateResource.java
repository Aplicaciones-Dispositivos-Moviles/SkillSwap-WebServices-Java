package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * An approved result that a Verificador senior can order to evaluate again. It has nothing about the student.
 *
 * @param sourceType     Attempt (a passed quiz attempt) or Case (an approved verification case)
 * @param sourceId       the id to send as sourceId in POST /api/v1/reevaluations
 * @param skillTag       the skill that was assessed
 * @param caseType       Quiz or MiniProject for a case; null for an attempt
 * @param resolvedAt     when the case was approved or the attempt was completed (UTC)
 * @param approvedByName the registered name of the verifier who approved a case; null for an attempt
 */
public record ReevaluationCandidateResource(@Schema(allowableValues = {"Attempt", "Case"}) String sourceType,
                                            int sourceId, String skillTag,
                                            @Schema(allowableValues = {"Quiz", "MiniProject"}) String caseType,
                                            Instant resolvedAt, String approvedByName) {
}
