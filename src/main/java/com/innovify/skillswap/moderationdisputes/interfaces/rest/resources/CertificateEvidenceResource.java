package com.innovify.skillswap.moderationdisputes.interfaces.rest.resources;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The certificate under review, as the reviewer sees it (without its owner).
 *
 * @param id                 the certificate
 * @param holderName         holder read by the OCR
 * @param institutionName    issuing institution
 * @param courseName         course or program
 * @param issueDate          issue date
 * @param certificateNumber  certificate number
 * @param verificationCode   verification code
 * @param verificationUrl    official verification URL
 * @param status             Suspicious while it waits, then Verified or Rejected
 * @param riskLevel          LowRisk, Review or HighRisk
 * @param holderNameMismatch whether the holder is not the registered name of the owner
 * @param createdAt          when it was registered (UTC)
 * @param fileUrl            temporary signed URL to view the file (expires in 15 minutes)
 */
public record CertificateEvidenceResource(int id, String holderName, String institutionName, String courseName,
                                          LocalDate issueDate, String certificateNumber, String verificationCode,
                                          String verificationUrl, String status, String riskLevel,
                                          boolean holderNameMismatch, Instant createdAt, String fileUrl) {
}
