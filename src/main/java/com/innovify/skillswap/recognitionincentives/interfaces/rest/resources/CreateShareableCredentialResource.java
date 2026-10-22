package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

/**
 * Asks for the public link of a verified skill.
 *
 * @param skillTag the skill the student completed
 */
public record CreateShareableCredentialResource(String skillTag) {
}
