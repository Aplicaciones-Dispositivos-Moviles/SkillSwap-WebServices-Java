package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

/**
 * The decision of the verifier.
 *
 * @param decision    Approved or Rejected (any letter case)
 * @param rubricNotes the notes of the rubric (required, up to 2000 characters)
 */
public record ResolveVerificationCaseResource(String decision, String rubricNotes) {
}
