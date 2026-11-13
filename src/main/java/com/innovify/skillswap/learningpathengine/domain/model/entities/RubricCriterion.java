package com.innovify.skillswap.learningpathengine.domain.model.entities;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.Objects;

/** One criterion of the rubric of a mini-project, with the weight (1 to 100) it has in the score. Immutable. */
public final class RubricCriterion {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_WEIGHT = 100;

    private final String name;
    private final String description;
    private final int weight;

    /**
     * @throws DomainException when the name or the description are empty or too long, or the weight is not between
     *                         1 and {@value #MAX_WEIGHT}
     */
    public RubricCriterion(String name, String description, int weight) {
        if (name == null || name.isBlank()) {
            throw new DomainException("The name of a rubric criterion cannot be empty.");
        }
        if (name.strip().length() > MAX_NAME_LENGTH) {
            throw new DomainException(
                    "The name of a rubric criterion cannot exceed %d characters.".formatted(MAX_NAME_LENGTH));
        }
        if (description == null || description.isBlank()) {
            throw new DomainException("The description of a rubric criterion cannot be empty.");
        }
        if (description.strip().length() > MAX_DESCRIPTION_LENGTH) {
            throw new DomainException("The description of a rubric criterion cannot exceed %d characters."
                    .formatted(MAX_DESCRIPTION_LENGTH));
        }
        if (weight < 1 || weight > MAX_WEIGHT) {
            throw new DomainException(
                    "The weight of a rubric criterion must be between 1 and %d.".formatted(MAX_WEIGHT));
        }

        this.name = name.strip();
        this.description = description.strip();
        this.weight = weight;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RubricCriterion criterion
                && weight == criterion.weight
                && name.equals(criterion.name)
                && description.equals(criterion.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, weight);
    }
}
