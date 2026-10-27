package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * A student uploads (or replaces) the video of a final demonstration.
 *
 * @param demonstrationId the demonstration
 * @param studentId       the student (the authenticated user)
 * @param content         the raw video (MP4, WebM or QuickTime, up to 25 MB)
 * @param contentType     the declared MIME type, which must match the real content
 */
public record SubmitDemonstrationVideoCommand(int demonstrationId, int studentId, byte[] content,
                                              String contentType) {
}
