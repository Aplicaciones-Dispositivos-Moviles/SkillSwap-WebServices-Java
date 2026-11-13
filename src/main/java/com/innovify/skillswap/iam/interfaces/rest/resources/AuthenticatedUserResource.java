package com.innovify.skillswap.iam.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a successful sign-in: basic user information plus the JWT.
 *
 * @param id         the unique identifier of the user
 * @param username   the username
 * @param email      the institutional email
 * @param role       the account role: Student
 * @param isVerified whether the institutional validation has been completed
 * @param token      the JWT to send as a Bearer token
 */
public record AuthenticatedUserResource(int id, String username, String email, String role,
                                        @JsonProperty("isVerified") boolean isVerified, String token) {
}
