package com.innovify.skillswap.credentialverification.domain.model.commands;

import java.time.LocalDate;

/**
 * Upload certificate command.
 *
 * @param ownerId         the authenticated student who owns the certificate (taken from the token, never from
 *                        the body)
 * @param contentType     the MIME type declared by the client
 * @param fileContent     the raw content of the file
 * @param holderName      holder name read by the OCR, if any
 * @param institutionName issuing institution read by the OCR, if any
 * @param courseName      course or program name read by the OCR, if any
 * @param issueDate       issue date read by the OCR, if any
 * @param durationHours   duration in hours read by the OCR, if any
 * @param certificateNumber certificate number read by the OCR, if any
 * @param verificationCode  verification code read by the OCR, if any
 * @param verificationUrl   official verification URL read by the OCR, if any
 * @param qrPayload       decoded content of the QR code, if any
 * @param ocrText         full text recognized by the OCR, kept for audit and reprocessing
 */
public record UploadCertificateCommand(
        int ownerId,
        String contentType,
        byte[] fileContent,
        String holderName,
        String institutionName,
        String courseName,
        LocalDate issueDate,
        Integer durationHours,
        String certificateNumber,
        String verificationCode,
        String verificationUrl,
        String qrPayload,
        String ocrText) {
}
