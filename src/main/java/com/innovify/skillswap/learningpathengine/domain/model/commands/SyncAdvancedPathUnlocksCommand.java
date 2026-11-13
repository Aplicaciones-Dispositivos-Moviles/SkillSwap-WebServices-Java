package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * Grants any advanced path the student redeemed that was not granted yet (for example, when the event of the
 * redemption was lost) and answers all their unlocks.
 */
public record SyncAdvancedPathUnlocksCommand(int studentId) {
}
