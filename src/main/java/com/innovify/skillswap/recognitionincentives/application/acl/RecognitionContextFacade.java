package com.innovify.skillswap.recognitionincentives.application.acl;

import java.util.List;

/**
 * Anti-corruption facade through which other bounded contexts (Learning Path Engine) read what a user redeemed,
 * without depending on the wallets or their movements.
 */
public interface RecognitionContextFacade {

    /** The ids of the advanced path unlocks the user redeemed, oldest first; empty without a wallet. */
    List<Integer> getAdvancedPathUnlockRedemptionIds(int userId);
}
