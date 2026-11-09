package com.innovify.skillswap.assessmentpeerreview.domain.model.queries;

/**
 * The approved results a Verificador senior can order to evaluate again.
 *
 * @param seniorUserId the verifier who asks; their own results are never candidates
 * @param skillTag     only the results of that skill; null or blank for all
 * @param limit        how many results at most (20 when missing or not positive, never more than 50)
 */
public record GetReevaluationCandidatesQuery(int seniorUserId, String skillTag, Integer limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    public GetReevaluationCandidatesQuery {
        skillTag = skillTag == null || skillTag.isBlank() ? null : skillTag.strip();
        limit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
