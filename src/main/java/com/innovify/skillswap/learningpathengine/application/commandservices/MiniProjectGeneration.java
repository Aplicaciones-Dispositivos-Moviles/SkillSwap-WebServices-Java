package com.innovify.skillswap.learningpathengine.application.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;

/**
 * The mini-project a student asked for.
 *
 * @param miniProject the mini-project of the node, saved
 * @param created     true when it was just generated; false when the latest one was returned because it can still be
 *                    delivered
 */
public record MiniProjectGeneration(MiniProject miniProject, boolean created) {
}
