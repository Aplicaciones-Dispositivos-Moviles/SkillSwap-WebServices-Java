package com.innovify.skillswap.iam.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User resource for the REST API.
 *
 * @param id         the unique identifier of the user
 * @param username   the username
 * @param email      the institutional email
 * @param role       the account role: Student
 * @param isVerified whether the institutional validation has been completed
 * @param bio        the profile description
 * @param fullName   the registered real name, compared with the holder of the certificates; null when not given
 */
public record UserResource(int id, String username, String email, String role,
                           @JsonProperty("isVerified") boolean isVerified, String bio, String fullName) {
}
