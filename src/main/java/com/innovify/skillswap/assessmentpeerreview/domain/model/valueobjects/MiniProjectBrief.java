package com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects;

import java.util.List;

/**
 * What the delivery of a mini-project is evaluated against: the skill, what the student had to build and the rubric.
 * It is a copy made by this context of the mini-project of Learning Path Engine, so the domain never depends on it.
 *
 * @param skillTag     the skill being demonstrated
 * @param title        the title of the mini-project
 * @param statement    what the student had to build
 * @param rubric       the criteria, whose weights add up to 100
 * @param passingScore the score (out of 100) the delivery needs to be approved
 */
public record MiniProjectBrief(String skillTag, String title, String statement, List<Criterion> rubric,
                               int passingScore) {

    public MiniProjectBrief {
        rubric = rubric == null ? List.of() : List.copyOf(rubric);
    }

    /**
     * One criterion of the rubric.
     *
     * @param name        short name
     * @param description what must be observed in the repository
     * @param weight      the points it is worth (out of 100)
     */
    public record Criterion(String name, String description, int weight) {
    }
}
