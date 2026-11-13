package com.innovify.skillswap.learningpathengine.domain.model.commands;

/** Takes a completed node back to available (requested by Assessment &amp; Peer Review through the context facade). */
public record ReopenPathNodeCommand(int pathNodeId) {
}
