package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

/**
 * The SkillCredits wallet of a user.
 *
 * @param id            the wallet
 * @param walletOwnerId the user who owns it
 * @param balance       the available SkillCredits
 */
public record WalletResource(int id, int walletOwnerId, int balance) {
}
