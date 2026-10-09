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
 */
public record VerifierProfileResource(int id, int verifierUserId, List<String> skillTags, boolean available,
                                      boolean verified, double rating, int reviewCount, Instant createdAt) {
}
