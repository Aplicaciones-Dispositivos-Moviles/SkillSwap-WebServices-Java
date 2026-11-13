package com.innovify.skillswap.recognitionincentives.application.queryservices;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletByOwnerIdQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletTransactionsQuery;
import java.util.List;
import java.util.Optional;

/** Wallet query service interface. */
public interface WalletQueryService {

    /** The wallet of the user, or empty when they have none yet. */
    Optional<Wallet> handle(GetWalletByOwnerIdQuery query);

    /** The movements of the wallet of the user, newest first, or empty when they have no wallet yet. */
    Optional<List<CreditTransaction>> handle(GetWalletTransactionsQuery query);
}
