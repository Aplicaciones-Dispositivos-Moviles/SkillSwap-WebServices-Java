package com.innovify.skillswap.learningpathengine.domain.model.commands;

/** Links to the active path the certificates the student uploaded since it was created. */
public record RefreshCertificateLinksCommand(int studentId) {
}
