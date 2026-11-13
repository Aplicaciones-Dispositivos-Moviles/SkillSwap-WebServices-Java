package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.List;

/**
 * What Assessment &amp; Peer Review needs to know of a path to schedule its final demonstration.
 *
 * @param pathId    the path
 * @param studentId its owner
 * @param completed whether every node of the path is completed
 * @param skillTags the distinct skills of its nodes, in the order of the path
 */
public record PathCompletionView(int pathId, int studentId, boolean completed, List<String> skillTags) {
}
