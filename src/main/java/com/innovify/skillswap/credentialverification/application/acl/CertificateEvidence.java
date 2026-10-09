package com.innovify.skillswap.credentialverification.application.acl;

/**
 * View of a certificate that Learning Path Engine consumes as evidence of a skill, with the data extracted by the
 * OCR that it compares with the skill.
 *
 * @param id                 the certificate
 * @param ownerId            the student who owns it
 * @param courseName         the course or program name, if it was read
 * @param institutionName    the issuing institution, if it was read
 * @param ocrText            the full text recognized in the document (may be empty)
 * @param supportingEvidence whether it can support a skill: it is neither pending, suspicious nor rejected
 * @param validated          whether a verifier confirmed it (Verified): only then it counts as the skill already
 *                           demonstrated
 */
public record CertificateEvidence(int id, int ownerId, String courseName, String institutionName, String ocrText,
                                  boolean supportingEvidence, boolean validated) {
}
