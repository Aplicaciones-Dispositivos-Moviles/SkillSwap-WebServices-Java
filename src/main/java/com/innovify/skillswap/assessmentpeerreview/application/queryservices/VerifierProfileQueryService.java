package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerifierProfileByUserIdQuery;
import java.util.Optional;

/** Verifier profile query service interface. */
public interface VerifierProfileQueryService {

    Optional<VerifierProfile> handle(GetVerifierProfileByUserIdQuery query);
}
