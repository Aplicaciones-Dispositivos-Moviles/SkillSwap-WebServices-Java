package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.List;

/**
 * Minimal view of an assessment blueprint that other bounded contexts may consume to grade an attempt.
 *
 * @param blueprintId     the blueprint
 * @param pathNodeId      the node it assesses
 * @param studentId       the owner of the path the node belongs to
 * @param skillTag        the skill demonstrated by the node
 * @param isLatest        whether it is the latest blueprint generated for the node
 * @param nodeIsAvailable whether the node can still be demonstrated (neither locked nor completed, and its path is
 *                        not paused)
 * @param questions       the questions in order, with their correct answers
 */
public record BlueprintView(
        int blueprintId,
        int pathNodeId,
        int studentId,
        String skillTag,
        boolean isLatest,
        boolean nodeIsAvailable,
        List<BlueprintQuestionView> questions) {
}
