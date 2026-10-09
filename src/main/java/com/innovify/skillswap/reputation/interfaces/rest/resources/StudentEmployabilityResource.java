package com.innovify.skillswap.reputation.interfaces.rest.resources;

import java.time.Instant;

/**
 * The employability of a student.
 *
 * @param id                  the score
 * @param studentId           the student
 * @param verifiedSkillsCount how many skills they certified
 * @param score               from 0 to 100
 * @param updatedAt           when it last changed (UTC)
 */
public record StudentEmployabilityResource(int id, int studentId, int verifiedSkillsCount, int score,
                                           Instant updatedAt) {
}
