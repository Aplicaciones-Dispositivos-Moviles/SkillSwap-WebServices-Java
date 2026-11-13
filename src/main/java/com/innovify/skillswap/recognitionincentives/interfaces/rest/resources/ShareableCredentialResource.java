package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

import java.time.Instant;

/**
 * The public link of a verified skill of the student.
 *
 * @param url       the link anyone can open, without an account
 * @param shareText the text to share, in the language of the request
 * @param createdAt when the link was generated
 */
public record ShareableCredentialResource(String skillTag, String skillName, String token, String url,
                                          String shareText, Instant createdAt) {
}
