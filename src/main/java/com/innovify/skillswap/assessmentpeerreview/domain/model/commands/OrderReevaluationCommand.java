package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;

/**
 * A Verificador senior orders the reevaluation of an approved result, so the student demonstrates the skill again. A
 * null source type means the request did not carry a valid one, which the service rejects as InvalidSourceType.
 *
 * @param seniorUserId  the senior (the authenticated user)
 * @param sourceType    whether the approved result is an attempt or a verification case
 * @param sourceId      the id of the attempt or of the case
 * @param justification why the result is doubted (required, up to 2000 characters)
 */
public record OrderReevaluationCommand(int seniorUserId, ReevaluationSourceType sourceType, int sourceId,
                                       String justification) {
}
