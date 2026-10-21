package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.List;

/**
 * What another bounded context needs to evaluate the delivery of a mini-project: its statement, its rubric and the
 * score the delivery must reach.
 *
 * @param miniProjectId the mini-project
 * @param skillTag      the skill it demonstrates
 * @param title         its title
 * @param statement     what the student had to build
 * @param rubric        the criteria of the rubric, whose weights add up to 100
 * @param passingScore  the score (out of 100) the delivery needs to be approved by the preliminary evaluation
 */
public record MiniProjectRubricView(
        int miniProjectId,
        String skillTag,
        String title,
        String statement,
        List<RubricCriterionView> rubric,
        int passingScore) {

    public MiniProjectRubricView {
        rubric = rubric == null ? List.of() : List.copyOf(rubric);
    }
}
