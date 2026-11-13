package com.innovify.skillswap.recognitionincentives.application.queryservices;

import java.time.Instant;

/**
 * What anyone with the link can see. It has no email or id of the student.
 *
 * @param fullName     the name the student registered; null when unknown
 * @param skillCredits the SkillCredits of the student right now
 * @param issuedAt     when the link was generated
 */
public record PublicCredentialView(String fullName, String skillTag, String skillName, int skillCredits,
                                   Instant issuedAt) {
}
