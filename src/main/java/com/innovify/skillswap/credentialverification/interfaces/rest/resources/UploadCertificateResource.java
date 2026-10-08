package com.innovify.skillswap.credentialverification.interfaces.rest.resources;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart form sent by the mobile app: the certificate file plus the fields the on-device OCR managed to
 * read. Everything except the file is optional. The owner is never part of the form: it is always taken from
 * the authenticated user, and any other member sent by the client is ignored.
 *
 * @param file              the certificate file (JPG, PNG or PDF, up to 10 MB)
 * @param holderName        holder name read by the OCR
 * @param institutionName   issuing institution read by the OCR
 * @param courseName        course or program name read by the OCR
 * @param issueDate         issue date read by the OCR (yyyy-MM-dd)
 * @param durationHours     duration in hours read by the OCR
 * @param certificateNumber certificate number read by the OCR
 * @param verificationCode  verification code read by the OCR
 * @param verificationUrl   official verification URL read by the OCR
 * @param qrPayload         decoded content of the QR code
 * @param ocrText           full text recognized by the OCR
 */
public record UploadCertificateResource(
        MultipartFile file,
        String holderName,
        String institutionName,
        String courseName,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
        Integer durationHours,
        String certificateNumber,
        String verificationCode,
        String verificationUrl,
        String qrPayload,
        String ocrText) {
}
