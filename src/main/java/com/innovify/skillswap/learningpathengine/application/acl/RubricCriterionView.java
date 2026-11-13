package com.innovify.skillswap.learningpathengine.application.acl;

/**
 * One criterion of the rubric of a mini-project, as other bounded contexts see it.
 *
 * @param name        short name of the criterion
 * @param description what is observed in the delivery
 * @param weight      its weight in the score (1 to 100)
 */
public record RubricCriterionView(String name, String description, int weight) {
}
