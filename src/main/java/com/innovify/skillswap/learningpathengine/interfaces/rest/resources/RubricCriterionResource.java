package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/** A criterion of the rubric of a mini-project; the weights of all the criteria add up to 100. */
public record RubricCriterionResource(String name, String description, int weight) {
}
