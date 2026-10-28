package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import java.time.Instant;

/**
 * An approved result a Verificador senior can order to evaluate again. It carries nothing about the student.
 *
 * @param sourceType     whether it is a passed attempt or an approved case
 * @param sourceId       the id of the attempt or of the case
 * @param skillTag       the skill that was assessed
 * @param caseType       what the student handed in for a case; null for an attempt
 * @param resolvedAt     when the case was approved or the attempt was completed
 * @param approvedByName the registered name of the verifier who approved a case; null for an attempt or when the
 *                       verifier gave no name
 */
public record ReevaluationCandidate(ReevaluationSourceType sourceType, int sourceId, String skillTag,
                                    CaseType caseType, Instant resolvedAt, String approvedByName) {
}
