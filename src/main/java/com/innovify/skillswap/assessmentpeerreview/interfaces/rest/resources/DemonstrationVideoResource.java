package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.time.Instant;

/**
 * A temporary link to the video of a final demonstration.
 *
 * @param url         the signed URL; it stops working at {@code expiresAt}, so ask for it again when needed
 * @param contentType the MIME type of the video (video/mp4, video/webm or video/quicktime)
 * @param expiresAt   when the link stops working (UTC)
 */
public record DemonstrationVideoResource(String url, String contentType, Instant expiresAt) {
}
