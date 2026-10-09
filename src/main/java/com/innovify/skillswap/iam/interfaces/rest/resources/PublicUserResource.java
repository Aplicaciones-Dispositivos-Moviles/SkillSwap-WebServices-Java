package com.innovify.skillswap.iam.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Public view of a user, visible to any authenticated user. Excludes private data such as the email.
 *
 * @param id         the unique identifier of the user
 * @param username   the username
 * @param role       the account role: Student
 * @param isVerified whether the institutional validation has been completed
 * @param bio        the profile description
 * @param interests  the interest topics of the profile
 * @param skillVector the catalog skill tags the interests and the description refer to
 */
public record PublicUserResource(int id, String username, String role,
                                 @JsonProperty("isVerified") boolean isVerified, String bio, List<String> interests,
                                 List<String> skillVector) {
}
