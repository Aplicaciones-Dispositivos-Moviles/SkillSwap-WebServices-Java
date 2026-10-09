package com.innovify.skillswap.recognitionincentives.interfaces.rest.transform;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.resources.CreditTransactionResource;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.resources.WalletResource;

/** Converts the Recognition &amp; Incentives model into REST resources. */
public final class RecognitionIncentivesResourceAssemblers {

    private RecognitionIncentivesResourceAssemblers() {
    }

    public static WalletResource toResource(Wallet wallet) {
        return new WalletResource(wallet.getId(), wallet.getWalletOwnerId(), wallet.getBalance());
    }

    public static CreditTransactionResource toResource(CreditTransaction transaction) {
        return new CreditTransactionResource(transaction.getId(), transaction.getWalletId(),
                transaction.getAmount().value(), transaction.getType().value(), transaction.getDescription(),
                transaction.getRelatedCaseId(), transaction.getCreatedAt(),
                transaction.getRedemptionItem() == null ? null : transaction.getRedemptionItem().value());
    }
}
