package com.innovify.skillswap.reputation.domain.model.queries;

/** The skills and sub-topics with the most failures in a period. from and to are yyyy-MM-dd (inclusive), null for the default period. Only a Verificador senior can ask. */
public record GetDifficultyMetricsQuery(int requesterUserId, String from, String to, Integer limit) {
}
