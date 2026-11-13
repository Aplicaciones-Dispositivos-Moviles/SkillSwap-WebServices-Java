package com.innovify.skillswap.moderationdisputes.domain.services;

/**
 * A verifier who could review a dispute.
 *
 * @param userId   the verifier
 * @param senior   whether they are a Verificador senior right now
 * @param workload their unresolved verification cases plus their pending disputes
 */
public record ReviewerCandidate(int userId, boolean senior, int workload) {
}
