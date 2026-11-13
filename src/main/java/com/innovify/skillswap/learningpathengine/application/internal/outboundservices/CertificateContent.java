package com.innovify.skillswap.learningpathengine.application.internal.outboundservices;

/**
 * What the Learning Path Engine reads of a certificate to compare it with a skill: the data extracted on the
 * device by the OCR.
 *
 * @param courseName the course or program name, if it was read
 * @param ocrText    the full text recognized in the document, if any
 */
public record CertificateContent(String courseName, String ocrText) {
}
