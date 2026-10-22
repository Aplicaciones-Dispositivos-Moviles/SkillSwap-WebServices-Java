package com.innovify.skillswap.recognitionincentives.application.queryservices;

import java.time.Instant;

/**
 * A credential of the student with everything needed to share it.
 *
 * @param url       the public link of the credential
 * @param shareText the text to share, in the language of the request, with the skill, the SkillCredits and the url
 * @param createdAt when the link was generated
 */
public record ShareableCredentialView(String skillTag, String skillName, String token, String url, String shareText,
                                      Instant createdAt) {
}
