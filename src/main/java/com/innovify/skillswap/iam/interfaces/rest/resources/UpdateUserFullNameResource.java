package com.innovify.skillswap.iam.interfaces.rest.resources;

/**
 * Resource for updating the real name of the student.
 *
 * @param fullName the name the certificates are issued to (up to 150 characters); null or blank clears it
 */
public record UpdateUserFullNameResource(String fullName) {
}
