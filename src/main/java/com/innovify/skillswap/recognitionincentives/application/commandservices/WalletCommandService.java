package com.innovify.skillswap.recognitionincentives.application.commandservices;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateWalletCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.RedeemCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.shared.application.Result;

/** Wallet command service interface. Nobody gives credits to anybody: they are earned only through events. */
public interface WalletCommandService {

    /** Creates the empty wallet of a user; answers the existing one when there is already one. */
    Result<Wallet> handle(CreateWalletCommand command);

    /**
     * Credits a verifier for a resolved case, creating their wallet if they have none yet. A case pays a wallet
     * only once, so repeating the command changes nothing.
     */
    Result<Wallet> handle(CreditVerifierCommand command);

    /**
     * Redeems a benefit, taking its cost from the balance and recording the movement. Fails with
     * INVALID_REDEMPTION_ITEM, WALLET_NOT_FOUND or INSUFFICIENT_BALANCE.
     */
    Result<CreditTransaction> handle(RedeemCommand command);
}
