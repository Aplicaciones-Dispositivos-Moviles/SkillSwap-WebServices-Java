package com.innovify.skillswap.recognitionincentives.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RecognitionIncentivesPersistenceTest extends PostgresIntegrationTest {

    @Autowired
    private WalletRepository wallets;

    @Autowired
    private CreditTransactionRepository transactions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private CreditTransaction earned(int walletId, int amount, Integer caseId) {
        return transactions.save(new CreditTransaction(walletId, new Credits(amount), TransactionType.EARNED,
                "Verification case resolved", caseId));
    }

    // ---------- Wallets ----------

    @Test
    void wallet_isStoredWithItsOwnerAndBalance() {
        Wallet wallet = new Wallet(3).credit(new Credits(40));
        wallets.save(wallet);

        Wallet loaded = wallets.findByOwnerId(3).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getWalletOwnerId()).isEqualTo(3);
        assertThat(loaded.getBalance()).isEqualTo(40);
    }

    @Test
    void wallet_isUpdatedInPlace() throws Exception {
        wallets.save(new Wallet(3));

        Wallet loaded = wallets.findByOwnerId(3).orElseThrow();
        loaded.credit(new Credits(10));
        wallets.save(loaded);

        assertThat(queryString("SELECT count(*) FROM wallets")).isEqualTo("1");
        assertThat(wallets.findByOwnerId(3).orElseThrow().getBalance()).isEqualTo(10);
    }

    @Test
    void wallet_findsNothingForAUserWithoutWallet() {
        assertThat(wallets.findByOwnerId(99)).isEmpty();
    }

    @Test
    void wallet_rejectsASecondWalletForTheSameOwner() {
        wallets.save(new Wallet(3));

        assertThatThrownBy(() -> wallets.save(new Wallet(3))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void wallet_theDatabaseRefusesANegativeBalance() {
        wallets.save(new Wallet(3));

        assertThatThrownBy(() -> execute("UPDATE wallets SET balance = -1")).isInstanceOf(SQLException.class);
    }

    @Test
    void wallet_canBeReadWithALockInsideATransaction() {
        wallets.save(new Wallet(3).credit(new Credits(10)));

        Integer balance = new TransactionTemplate(transactionManager)
                .execute(status -> wallets.findByOwnerIdForUpdate(3).orElseThrow().getBalance());

        assertThat(balance).isEqualTo(10);
    }

    // ---------- Transactions ----------

    @Test
    void transaction_isStoredWithItsTypeAmountAndCase() {
        Wallet wallet = wallets.save(new Wallet(3));
        CreditTransaction saved = earned(wallet.getId(), 10, 7);

        CreditTransaction loaded = transactions.findByWalletId(wallet.getId()).get(0);

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getWalletId()).isEqualTo(wallet.getId());
        assertThat(loaded.getAmount().value()).isEqualTo(10);
        assertThat(loaded.getType()).isEqualTo(TransactionType.EARNED);
        assertThat(loaded.getDescription()).isEqualTo("Verification case resolved");
        assertThat(loaded.getRelatedCaseId()).isEqualTo(7);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void transaction_isStoredAsTheTextTheCSharpApiWrote() throws Exception {
        Wallet wallet = wallets.save(new Wallet(3));
        transactions.save(new CreditTransaction(wallet.getId(), new Credits(30), TransactionType.REDEEMED,
                "Redeemed: contribution certificate", null));

        assertThat(queryString("SELECT type FROM credit_transactions")).isEqualTo("Redeemed");
    }

    @Test
    void transactions_areListedNewestFirst() {
        Wallet wallet = wallets.save(new Wallet(3));
        CreditTransaction first = earned(wallet.getId(), 10, 1);
        CreditTransaction second = earned(wallet.getId(), 10, 2);
        CreditTransaction third = transactions.save(new CreditTransaction(wallet.getId(), new Credits(20),
                TransactionType.REDEEMED, "Redeemed: something", null));

        List<CreditTransaction> listed = transactions.findByWalletId(wallet.getId());

        assertThat(listed).extracting(CreditTransaction::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void transactions_doNotMixTheMovementsOfOtherWallets() {
        Wallet mine = wallets.save(new Wallet(3));
        Wallet other = wallets.save(new Wallet(4));
        earned(other.getId(), 10, 1);

        assertThat(transactions.findByWalletId(mine.getId())).isEmpty();
    }

    @Test
    void existsEarnedForCase_onlyCountsEarnedMovementsOfThatCaseAndWallet() {
        Wallet mine = wallets.save(new Wallet(3));
        Wallet other = wallets.save(new Wallet(4));
        earned(mine.getId(), 10, 7);
        transactions.save(new CreditTransaction(mine.getId(), new Credits(30), TransactionType.REDEEMED,
                "Redeemed: contribution certificate", null));

        assertThat(transactions.existsEarnedForCase(mine.getId(), 7)).isTrue();
        assertThat(transactions.existsEarnedForCase(mine.getId(), 8)).isFalse();
        assertThat(transactions.existsEarnedForCase(other.getId(), 7)).isFalse();
    }

    @Test
    void transaction_rejectsPayingTheSameCaseTwiceToAWallet() {
        Wallet wallet = wallets.save(new Wallet(3));
        earned(wallet.getId(), 10, 7);

        assertThatThrownBy(() -> earned(wallet.getId(), 10, 7)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transaction_allowsSeveralMovementsWithoutCase() {
        Wallet wallet = wallets.save(new Wallet(3));
        for (int i = 0; i < 2; i++) {
            transactions.save(new CreditTransaction(wallet.getId(), new Credits(30), TransactionType.REDEEMED,
                    "Redeemed: contribution certificate", null));
        }

        assertThat(transactions.findByWalletId(wallet.getId())).hasSize(2);
    }

    @Test
    void transaction_rejectsAWalletThatDoesNotExist() {
        assertThatThrownBy(() -> earned(99, 10, 7)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
