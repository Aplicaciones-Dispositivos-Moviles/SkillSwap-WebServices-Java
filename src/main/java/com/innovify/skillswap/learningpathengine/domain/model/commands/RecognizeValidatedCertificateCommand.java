package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * A verifier validated a certificate of the student: the skills it covers count as demonstrated in the paths
 * that are not completed yet.
 *
 * @param studentId     the owner of the certificate
 * @param certificateId the validated certificate
 */
public record RecognizeValidatedCertificateCommand(int studentId, int certificateId) {
}
