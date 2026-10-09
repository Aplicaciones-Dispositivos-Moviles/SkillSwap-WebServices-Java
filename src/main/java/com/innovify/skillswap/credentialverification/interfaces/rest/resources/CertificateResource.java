package com.innovify.skillswap.credentialverification.interfaces.rest.resources;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Certificate resource for the REST API. The file hash, the storage reference, the OCR text, the QR payload and
 * the numeric risk score are deliberately not part of it.
 *
 * @param id                the unique identifier of the certificate
 * @param ownerId           the student who owns the certificate
 * @param holderName        holder name
 * @param institutionName   issuing institution
 * @param courseName        course or program name
 * @param issueDate         issue date
 * @param durationHours     duration in hours
 * @param certificateNumber certificate number
 * @param verificationCode  verification code
 * @param verificationUrl   official verification URL
 * @param status            Unverified, Suspicious, Verified or Rejected
 * @param verificationMethod OcrOnly or Manual in the implemented scope
 * @param riskLevel         LowRisk, Review or HighRisk
 * @param createdAt         when the certificate was registered (UTC)
 * @param verifiedAt        when a verifier resolved it (UTC), if ever
 * @param fileUrl           temporary signed URL to view the file (expires in 15 minutes)
 */
public record CertificateResource(
        int id,
        int ownerId,
        String holderName,
        String institutionName,
        String courseName,
        LocalDate issueDate,
        Integer durationHours,
        String certificateNumber,
        String verificationCode,
        String verificationUrl,
        String status,
        String verificationMethod,
        String riskLevel,
        Instant createdAt,
        Instant verifiedAt,
        String fileUrl) {
}
