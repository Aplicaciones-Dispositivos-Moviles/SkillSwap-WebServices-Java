package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/**
 * Resource for asking which skills of the catalog fit a goal.
 *
 * @param goal the goal in the student's own words (1 to 500 characters)
 */
public record GetSkillOptionsResource(String goal) {
}
