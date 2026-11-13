package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

/**
 * A verifier profile.
 *
 * @param id             the profile
 * @param verifierUserId the user
 * @param skillTags      the skills they can review
 * @param available      whether they receive new cases
 * @param verified       false once the profile is revoked
 * @param rating         the average rating
 * @param reviewCount    how many cases they resolved
 * @param createdAt      when the profile was created (UTC)
 * @param suspended      true while a Verificador senior keeps them suspended: they receive no new cases
 * @param suspendedAt    when they were suspended (UTC); null when they are not suspended
 * @param suspensionReason the justification of the senior; null when they are not suspended
 */
public record VerifierProfileResource(int id, int verifierUserId, List<String> skillTags, boolean available,
                                      boolean verified, double rating, int reviewCount, Instant createdAt,
                                      boolean suspended, Instant suspendedAt, String suspensionReason) {
}
