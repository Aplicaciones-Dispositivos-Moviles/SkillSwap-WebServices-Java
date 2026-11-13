package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The order of a reevaluation.
 *
 * @param sourceType    Attempt (a passed attempt) or Case (an approved verification case); any letter case
 * @param sourceId      the id of the attempt or of the case
 * @param justification why the result is doubted (required, up to 2000 characters)
 */
public record OrderReevaluationResource(@Schema(allowableValues = {"Attempt", "Case"}) String sourceType,
                                        Integer sourceId, String justification) {
}
