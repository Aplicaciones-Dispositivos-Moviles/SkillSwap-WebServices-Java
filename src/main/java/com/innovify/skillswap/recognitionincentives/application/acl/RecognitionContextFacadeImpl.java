package com.innovify.skillswap.recognitionincentives.application.acl;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import java.util.List;

/** Facade implementation over the wallet and transaction repositories. */
public class RecognitionContextFacadeImpl implements RecognitionContextFacade {

    private final WalletRepository wallets;
    private final CreditTransactionRepository transactions;

    public RecognitionContextFacadeImpl(WalletRepository wallets, CreditTransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Override
    public List<Integer> getAdvancedPathUnlockRedemptionIds(int userId) {
        return wallets.findByOwnerId(userId)
                .map(wallet -> transactions.findRedemptionIds(wallet.getId(), RedemptionItem.ADVANCED_PATH_UNLOCK))
                .orElse(List.of());
    }
}
