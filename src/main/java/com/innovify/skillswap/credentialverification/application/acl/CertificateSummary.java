package com.innovify.skillswap.credentialverification.application.acl;

/** Minimal view of a certificate that other bounded contexts may consume. */
public record CertificateSummary(int id, String courseName, String institutionName) {
}
