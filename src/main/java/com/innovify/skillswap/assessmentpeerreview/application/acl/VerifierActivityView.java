package com.innovify.skillswap.assessmentpeerreview.application.acl;

/**
 * What the verification cases say about a verifier.
 *
 * @param verifierUserId         the verifier
 * @param suspended              whether a Verificador senior keeps them suspended
 * @param averageResolutionHours the average time, in hours rounded to two decimals, between the assignment of a case
 *                               and its resolution, over the cases they resolved; null when they resolved none
 */
public record VerifierActivityView(int verifierUserId, boolean suspended, Double averageResolutionHours) {
}
