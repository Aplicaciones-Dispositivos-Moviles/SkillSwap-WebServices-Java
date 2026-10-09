package com.innovify.skillswap.recognitionincentives.domain.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import java.util.List;

/** Persistence port of the {@link CreditTransaction} entity. */
public interface CreditTransactionRepository {

    /** Persists a new movement and flushes right away. */
    CreditTransaction save(CreditTransaction transaction);

    /** The movements of a wallet, newest first. */
    List<CreditTransaction> findByWalletId(int walletId);

    /** Whether the wallet already earned credits for the case. */
    boolean existsEarnedForCase(int walletId, int caseId);

    /** The ids of the redemptions of the wallet that bought the benefit, oldest first. */
    List<Integer> findRedemptionIds(int walletId, RedemptionItem item);
}
