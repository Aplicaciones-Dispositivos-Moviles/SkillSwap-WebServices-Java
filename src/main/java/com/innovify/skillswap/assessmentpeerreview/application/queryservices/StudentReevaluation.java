package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import java.time.Instant;

/**
 * A reevaluation ordered on a result of the student, as the student sees it. It carries nothing about the senior who
 * ordered it.
 *
 * @param id            the id of the reevaluation
 * @param sourceType    whether the result was a passed attempt or an approved case
 * @param sourceId      the id of the attempt or of the case
 * @param pathNodeId    the node of the path that was reopened; null when the result can no longer be found
 * @param skillTag      the skill that was assessed; null when the result can no longer be found
 * @param justification why the result was doubted
 * @param createdAt     when the reevaluation was ordered
 */
public record StudentReevaluation(int id, ReevaluationSourceType sourceType, int sourceId, Integer pathNodeId,
                                  String skillTag, String justification, Instant createdAt) {
}
