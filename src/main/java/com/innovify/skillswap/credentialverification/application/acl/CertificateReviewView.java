package com.innovify.skillswap.credentialverification.application.acl;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What the verifier who reviews a suspicious certificate needs to decide: the data read from the document, how
 * risky it was found and a temporary link to the file. The file hash and the OCR text stay in this context.
 *
 * @param id                 the certificate
 * @param ownerId            the student who registered it
 * @param holderName         holder read by the OCR
 * @param institutionName    issuing institution
 * @param courseName         course or program
 * @param issueDate          issue date
 * @param certificateNumber  certificate number
 * @param verificationCode   verification code
 * @param verificationUrl    official verification URL
 * @param status             Pending, Unverified, Suspicious, Verified or Rejected
 * @param riskLevel          LowRisk, Review or HighRisk
 * @param holderNameMismatch whether the holder is not the registered name of the owner
 * @param createdAt          when it was registered (UTC)
 * @param fileUrl            temporary signed URL to view the file
 */
public record CertificateReviewView(int id, int ownerId, String holderName, String institutionName,
                                    String courseName, LocalDate issueDate, String certificateNumber,
                                    String verificationCode, String verificationUrl, String status,
                                    String riskLevel, boolean holderNameMismatch, Instant createdAt,
                                    String fileUrl) {
}
