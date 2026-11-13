package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCasesByVerifierQuery;
import java.util.List;
import java.util.Optional;

/** Verification case query service interface. */
public interface VerificationCaseQueryService {

    Optional<VerificationCase> handle(GetVerificationCaseByIdQuery query);

    /** The case with its attempt and the failed questions; empty when the case or its attempt is missing. */
    Optional<VerificationCaseDetail> handle(GetVerificationCaseDetailQuery query);

    /** The cases assigned to the verifier, newest first. */
    List<VerificationCase> handle(GetVerificationCasesByVerifierQuery query);
}
