package com.innovify.skillswap.iam.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Public view of a user, visible to any authenticated user. Excludes private data such as the email.
 *
 * @param id         the unique identifier of the user
 * @param username   the username
 * @param role       the account role: Student or Coordinator
 * @param isVerified whether the institutional validation has been completed
 * @param bio        the profile description
 */
public record PublicUserResource(int id, String username, String role,
                                 @JsonProperty("isVerified") boolean isVerified, String bio) {
}
