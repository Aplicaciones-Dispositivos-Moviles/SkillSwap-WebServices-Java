package com.innovify.skillswap.credentialverification.application.acl;

/**
 * How many certificates of a course were uploaded in a period.
 *
 * @param courseName the name of the course as it was first written (the same course written with other letter case
 *                   or extra spaces counts as one)
 * @param count      the certificates uploaded in the period that were not rejected
 */
public record CertificationDemandView(String courseName, int count) {
}
