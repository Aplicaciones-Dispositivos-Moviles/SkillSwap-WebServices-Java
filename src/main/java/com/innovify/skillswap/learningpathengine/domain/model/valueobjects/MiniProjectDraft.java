package com.innovify.skillswap.learningpathengine.domain.model.valueobjects;

import com.innovify.skillswap.learningpathengine.domain.model.entities.RubricCriterion;
import java.util.List;

/**
 * What the generator proposes for a mini-project: the title, the statement and the rubric. It is only a proposal; the
 * {@code MiniProject} that receives it decides whether it meets the rules.
 */
public record MiniProjectDraft(String title, String statement, List<RubricCriterion> rubric) {
}
