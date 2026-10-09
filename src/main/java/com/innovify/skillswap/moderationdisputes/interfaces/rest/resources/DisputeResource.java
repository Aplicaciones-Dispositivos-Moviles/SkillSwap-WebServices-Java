package com.innovify.skillswap.moderationdisputes.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A dispute. The parties (who raised it and whose certificate it is) are deliberately not part of it, so the review
 * stays blind.
 *
 * @param id                     the dispute
 * @param sourceType             CertificateReview (the only origin opened today)
 * @param sourceReferenceId      the certificate under review
 * @param reason                 the rules that made it suspicious, e.g. "DuplicateFile, HolderNameMismatch"
 * @param status                 Pending or Resolved
 * @param outcome                Upheld (legitimate) or Overturned (fraudulent); null until resolved
 * @param resolutionNotes       the observations of the reviewer; null until resolved
 * @param assignedVerifierUserId the reviewer; null while nobody is available
 * @param assignedToSenior       whether the reviewer is a Verificador senior (false when no senior was available)
 * @param assignedAt             when it was assigned (UTC)
 * @param raisedAt               when it was opened (UTC)
 * @param resolvedAt             when it was resolved (UTC)
 */
public record DisputeResource(int id,
                              @Schema(allowableValues = {"CertificateReview", "VerifierDecisionAppeal", "UserReport"})
                              String sourceType,
                              int sourceReferenceId, String reason,
                              @Schema(allowableValues = {"Pending", "Resolved"}) String status,
                              String outcome, String resolutionNotes, Integer assignedVerifierUserId,
                              boolean assignedToSenior, Instant assignedAt, Instant raisedAt, Instant resolvedAt) {
}
