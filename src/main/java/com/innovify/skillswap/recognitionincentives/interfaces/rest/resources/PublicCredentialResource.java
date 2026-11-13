package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

import java.time.Instant;

/**
 * What anyone with the link sees. It has no email or id of the student.
 *
 * @param fullName     the name the student registered; null when unknown
 * @param skillCredits the SkillCredits of the student right now
 * @param issuedAt     when the link was generated
 */
public record PublicCredentialResource(String fullName, String skillTag, String skillName, int skillCredits,
                                       Instant issuedAt) {
}
