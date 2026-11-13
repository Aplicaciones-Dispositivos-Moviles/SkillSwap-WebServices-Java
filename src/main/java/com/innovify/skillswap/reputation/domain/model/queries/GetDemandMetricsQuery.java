package com.innovify.skillswap.reputation.domain.model.queries;

/** The most requested skills and the most frequent certifications of a period. from and to are yyyy-MM-dd (inclusive), null for the default period. Only a Verificador senior can ask. */
public record GetDemandMetricsQuery(int requesterUserId, String from, String to, Integer limit) {
}
