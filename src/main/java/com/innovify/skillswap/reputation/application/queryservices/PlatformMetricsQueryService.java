package com.innovify.skillswap.reputation.application.queryservices;

import com.innovify.skillswap.reputation.domain.model.queries.GetDemandMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetDifficultyMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierMetricsQuery;
import com.innovify.skillswap.shared.application.Result;

/** Platform metrics query service interface: aggregated data of other contexts, for the Verificadores senior. */
public interface PlatformMetricsQueryService {

    /**
     * The skills most asked for by learning paths and the certifications most uploaded in the period (the last 30 days
     * by default, at most 366 days). Fails with NOT_SENIOR_VERIFIER, INVALID_PERIOD, INVALID_LIMIT (1 to 50, 10 by
     * default), DATABASE_ERROR or INTERNAL_SERVER_ERROR.
     */
    Result<DemandMetrics> handle(GetDemandMetricsQuery query);

    /**
     * The skills with the highest failure rate (with at least 5 attempts) and the weakest sub-topics in the period.
     * Fails like the demand query.
     */
    Result<DifficultyMetrics> handle(GetDifficultyMetricsQuery query);

    /**
     * The verifiers with a reliability recorded, ordered by it (the highest first unless the order is asc), with their
     * average resolution time and whether they are suspended. Fails with NOT_SENIOR_VERIFIER, INVALID_ORDER,
     * DATABASE_ERROR or INTERNAL_SERVER_ERROR.
     */
    Result<VerifierMetrics> handle(GetVerifierMetricsQuery query);
}
