package com.innovify.skillswap.learningpathengine.domain.model.commands;

/** Completes an available node (requested by Assessment &amp; Peer Review through the context facade). */
public record CompletePathNodeCommand(int pathNodeId) {
}
