package com.innovify.skillswap.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Resource for registering or replacing the interest profile.
 *
 * @param topics      one to ten interest topics (up to 60 characters each); they replace the previous ones
 * @param description the profile description (up to 1000 characters); when omitted the current one is kept
 */
public record UpdateInterestProfileResource(@NotNull List<String> topics, String description) {
}
