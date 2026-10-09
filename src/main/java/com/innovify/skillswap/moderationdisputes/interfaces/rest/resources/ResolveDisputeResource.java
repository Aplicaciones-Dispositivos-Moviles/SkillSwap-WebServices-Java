package com.innovify.skillswap.moderationdisputes.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The decision of the reviewer.
 *
 * @param outcome          for a certificate review: Upheld (the certificate is legitimate and becomes Verified) or
 *                         Overturned (it is fraudulent and becomes Rejected)
 * @param coordinatorNotes the observations of the reviewer, required (up to 2000 characters)
 */
public record ResolveDisputeResource(@Schema(allowableValues = {"Upheld", "Overturned"}) String outcome,
                                     String coordinatorNotes) {
}
