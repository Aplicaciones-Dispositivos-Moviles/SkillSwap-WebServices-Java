package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetReevaluationCandidatesQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetStudentReevaluationsQuery;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;

/** Reevaluation query service interface. */
public interface ReevaluationQueryService {

    /**
     * The approved results the verifier can order to evaluate again, newest first: passed attempts and approved
     * cases of other students, without the ones that already were reevaluated or have a case in progress. Fails
     * with NOT_SENIOR_VERIFIER when the caller is not an enabled Verificador senior.
     */
    Result<List<ReevaluationCandidate>> handle(GetReevaluationCandidatesQuery query);

    /**
     * The reevaluations ordered on the results of the student, newest first, with the node that was reopened and the
     * skill when the result can still be found. Nothing about the senior who ordered them.
     */
    List<StudentReevaluation> handle(GetStudentReevaluationsQuery query);
}
