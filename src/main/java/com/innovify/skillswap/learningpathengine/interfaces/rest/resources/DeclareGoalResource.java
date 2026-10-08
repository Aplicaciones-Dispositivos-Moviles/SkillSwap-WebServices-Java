package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/**
 * Resource for declaring a career goal in free text.
 *
 * @param goal the goal in the student's own words (1 to 500 characters)
 */
public record DeclareGoalResource(String goal) {
}
