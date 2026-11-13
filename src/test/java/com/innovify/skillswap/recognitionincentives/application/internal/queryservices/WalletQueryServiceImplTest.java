package com.innovify.skillswap.recognitionincentives.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.recognitionincentives.application.fakes.FakeCreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.application.fakes.FakeWalletRepository;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletByOwnerIdQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletTransactionsQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import org.junit.jupiter.api.Test;

class WalletQueryServiceImplTest {

    private final FakeWalletRepository wallets = new FakeWalletRepository();
    private final FakeCreditTransactionRepository transactions = new FakeCreditTransactionRepository();
    private final WalletQueryServiceImpl service = new WalletQueryServiceImpl(wallets, transactions);

    @Test
    void walletQuery_returnsTheWalletOfTheUserOrEmpty() {
        Wallet wallet = wallets.save(new Wallet(2));

        assertThat(service.handle(new GetWalletByOwnerIdQuery(2))).containsSame(wallet);
        assertThat(service.handle(new GetWalletByOwnerIdQuery(3))).isEmpty();
    }

    @Test
    void transactionsQuery_listsTheMovementsNewestFirst() {
        Wallet wallet = wallets.save(new Wallet(2));
        CreditTransaction first = transactions.save(
                new CreditTransaction(wallet.getId(), new Credits(10), TransactionType.EARNED, "first", 1));
        CreditTransaction second = transactions.save(
                new CreditTransaction(wallet.getId(), new Credits(30), TransactionType.REDEEMED, "second", null));

        var result = service.handle(new GetWalletTransactionsQuery(2));

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(second, first);
    }

    @Test
    void transactionsQuery_ofAWalletWithoutMovements_isAnEmptyList() {
        wallets.save(new Wallet(2));

        assertThat(service.handle(new GetWalletTransactionsQuery(2))).contains(java.util.List.of());
    }

    @Test
    void transactionsQuery_ofAUserWithoutWallet_isEmpty() {
        assertThat(service.handle(new GetWalletTransactionsQuery(9))).isEmpty();
    }

    @Test
    void transactionsQuery_doesNotMixTheMovementsOfOtherWallets() {
        Wallet mine = wallets.save(new Wallet(2));
        Wallet other = wallets.save(new Wallet(3));
        transactions.save(new CreditTransaction(other.getId(), new Credits(10), TransactionType.EARNED, "x", 1));

        assertThat(service.handle(new GetWalletTransactionsQuery(mine.getWalletOwnerId()))).contains(java.util.List.of());
    }
}
