package com.innovify.skillswap.learningpathengine.application.acl;

import java.time.Instant;

/**
 * Minimal view of a mini-project that other bounded contexts may consume to receive its delivery.
 *
 * @param miniProjectId   the mini-project
 * @param pathNodeId      the node it assesses
 * @param studentId       the owner of the path the node belongs to
 * @param skillTag        the skill demonstrated by the node
 * @param dueAt           the deadline to deliver it (UTC)
 * @param isLatest        whether it is the latest mini-project generated for the node
 * @param nodeIsAvailable whether the node can still be demonstrated (neither locked nor completed, and its path is
 *                        not paused)
 */
public record MiniProjectView(
        int miniProjectId,
        int pathNodeId,
        int studentId,
        String skillTag,
        Instant dueAt,
        boolean isLatest,
        boolean nodeIsAvailable) {
}
