package com.innovify.skillswap.recognitionincentives.application.internal.queryservices;

import com.innovify.skillswap.recognitionincentives.application.queryservices.WalletQueryService;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletByOwnerIdQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletTransactionsQuery;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import java.util.List;
import java.util.Optional;

public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalletRepository wallets;
    private final CreditTransactionRepository transactions;

    public WalletQueryServiceImpl(WalletRepository wallets, CreditTransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Override
    public Optional<Wallet> handle(GetWalletByOwnerIdQuery query) {
        return wallets.findByOwnerId(query.ownerId());
    }

    @Override
    public Optional<List<CreditTransaction>> handle(GetWalletTransactionsQuery query) {
        return wallets.findByOwnerId(query.ownerId()).map(wallet -> transactions.findByWalletId(wallet.getId()));
    }
}
