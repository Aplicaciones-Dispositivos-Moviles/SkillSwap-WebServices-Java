package com.innovify.skillswap.recognitionincentives.domain.model.commands;

/**
 * Creates the empty wallet of a new account.
 *
 * @param ownerId the user
 */
public record CreateWalletCommand(int ownerId) {
}
