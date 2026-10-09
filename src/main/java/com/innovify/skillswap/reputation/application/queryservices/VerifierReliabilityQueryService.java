package com.innovify.skillswap.reputation.application.queryservices;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierReliabilityByUserIdQuery;
import java.util.Optional;

/** Verifier reliability query service interface. */
public interface VerifierReliabilityQueryService {

    Optional<VerifierReliability> handle(GetVerifierReliabilityByUserIdQuery query);
}
