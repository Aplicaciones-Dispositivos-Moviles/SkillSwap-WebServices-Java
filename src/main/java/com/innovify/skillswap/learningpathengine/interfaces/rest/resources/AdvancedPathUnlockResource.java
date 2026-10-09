package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * An advanced path unlock redeemed with SkillCredits.
 *
 * @param id             the unlock
 * @param redemptionId   the redeemed credit transaction that paid it
 * @param status         Available (start the advanced path with POST /api/v1/learning-paths and advanced true) or
 *                       Used
 * @param learningPathId the advanced path started with it; null while available
 * @param grantedAt      when it was granted (UTC)
 * @param usedAt         when it was used (UTC)
 */
public record AdvancedPathUnlockResource(int id, int redemptionId,
                                         @Schema(allowableValues = {"Available", "Used"}) String status,
                                         Integer learningPathId, Instant grantedAt, Instant usedAt) {
}
