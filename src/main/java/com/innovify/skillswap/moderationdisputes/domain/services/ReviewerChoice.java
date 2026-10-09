package com.innovify.skillswap.moderationdisputes.domain.services;

/**
 * The verifier chosen to review a dispute.
 *
 * @param userId the verifier
 * @param senior whether they are a Verificador senior; false when no senior could take it and another enabled
 *               verifier was chosen instead
 */
public record ReviewerChoice(int userId, boolean senior) {
}
