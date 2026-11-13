package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import java.time.Instant;

/**
 * A temporary link to the video of a final demonstration.
 *
 * @param url         the signed URL that gives temporary read access
 * @param contentType the MIME type of the video
 * @param expiresAt   when the link stops working (UTC)
 */
public record DemonstrationVideoLink(String url, String contentType, Instant expiresAt) {
}
