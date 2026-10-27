package com.innovify.skillswap.recognitionincentives.interfaces.rest.transform;

import com.innovify.skillswap.recognitionincentives.application.queryservices.PublicCredentialView;
import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialView;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.resources.CreditTransactionResource;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.resources.PublicCredentialResource;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.resources.ShareableCredentialResource;
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
                transaction.getRelatedCaseId(), transaction.getRelatedDemonstrationId(), transaction.getCreatedAt(),
                transaction.getRedemptionItem() == null ? null : transaction.getRedemptionItem().value());
    }

    public static ShareableCredentialResource toResource(ShareableCredentialView view) {
        return new ShareableCredentialResource(view.skillTag(), view.skillName(), view.token(), view.url(),
                view.shareText(), view.createdAt());
    }

    public static PublicCredentialResource toResource(PublicCredentialView view) {
        return new PublicCredentialResource(view.fullName(), view.skillTag(), view.skillName(),
                view.skillCredits(), view.issuedAt());
    }
}
