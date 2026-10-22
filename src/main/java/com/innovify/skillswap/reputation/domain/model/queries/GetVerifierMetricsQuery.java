package com.innovify.skillswap.reputation.domain.model.queries;

/** The verifiers ordered by reliability. order is asc or desc (null means desc). Only a Verificador senior can ask. */
public record GetVerifierMetricsQuery(int requesterUserId, String order) {
}
