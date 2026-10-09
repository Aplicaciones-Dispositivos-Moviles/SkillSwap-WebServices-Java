package com.innovify.skillswap.recognitionincentives.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.recognitionincentives.application.fakes.FakeCreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.application.fakes.FakeWalletRepository;
import com.innovify.skillswap.recognitionincentives.application.fakes.TestMessages;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateWalletCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.RedeemCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.ResolvedCaseType;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.domain.services.DefaultRedemptionPricing;
import com.innovify.skillswap.shared.application.Result;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

class WalletCommandServiceImplTest {

    private final FakeWalletRepository wallets = new FakeWalletRepository();
    private final FakeCreditTransactionRepository transactions = new FakeCreditTransactionRepository();
    private WalletCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = new WalletCommandServiceImpl(wallets, transactions, new DefaultRedemptionPricing(),
                TransactionOperations.withoutTransaction(), TestMessages.source());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Wallet walletWith(int ownerId, int balance) {
        Wallet wallet = new Wallet(ownerId);
        if (balance > 0) {
            wallet.credit(new Credits(balance));
        }
        return wallets.save(wallet);
    }

    /** A resolved quiz case, the only type the platform opens today. */
    private Result<Wallet> credit(int verifierUserId, int caseId) {
        return credit(verifierUserId, caseId, ResolvedCaseType.QUIZ);
    }

    private Result<Wallet> credit(int verifierUserId, int caseId, ResolvedCaseType caseType) {
        return service.handle(new CreditVerifierCommand(verifierUserId, caseId, caseType));
    }

    private Result<CreditTransaction> redeem(int userId, RedemptionItem item) {
        return service.handle(new RedeemCommand(userId, item));
    }

    private static void assertFailure(Result<?> result, RecognitionIncentivesError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
    }

    // ---------- Create wallet ----------

    @Test
    void createWallet_forANewUser_savesAnEmptyWallet() {
        Result<Wallet> result = service.handle(new CreateWalletCommand(3));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getWalletOwnerId()).isEqualTo(3);
        assertThat(result.value().getBalance()).isZero();
        assertThat(wallets.items()).hasSize(1);
    }

    @Test
    void createWallet_whenItAlreadyExists_answersTheExistingOne() {
        Wallet existing = walletWith(3, 20);

        Result<Wallet> result = service.handle(new CreateWalletCommand(3));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isSameAs(existing);
        assertThat(wallets.items()).hasSize(1);
        assertThat(result.value().getBalance()).isEqualTo(20);
    }

    @Test
    void createWallet_whenTheDatabaseFails_returnsDatabaseError() {
        wallets.failOnSave(new DataIntegrityViolationException("boom"));

        assertFailure(service.handle(new CreateWalletCommand(3)), RecognitionIncentivesError.DATABASE_ERROR);
    }

    @Test
    void createWallet_withAnInvalidUser_returnsInternalServerError() {
        assertFailure(service.handle(new CreateWalletCommand(0)), RecognitionIncentivesError.INTERNAL_SERVER_ERROR);
    }

    // ---------- Credit verifier ----------

    @Test
    void credit_aVerifierWithoutWallet_createsItWithTheQuizRewardAndTheMovement() {
        Result<Wallet> result = credit(2, 10);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getBalance()).isEqualTo(25);
        assertThat(wallets.items()).hasSize(1);
        assertThat(transactions.items()).hasSize(1);
        CreditTransaction movement = transactions.items().get(0);
        assertThat(movement.getWalletId()).isEqualTo(result.value().getId());
        assertThat(movement.getType()).isEqualTo(TransactionType.EARNED);
        assertThat(movement.getAmount().value()).isEqualTo(25);
        assertThat(movement.getRelatedCaseId()).isEqualTo(10);
        assertThat(movement.getDescription()).isEqualTo("Verification case resolved");
    }

    @Test
    void credit_aResolvedMiniProject_paysForty() {
        Result<Wallet> result = credit(2, 10, ResolvedCaseType.MINI_PROJECT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getBalance()).isEqualTo(40);
        assertThat(transactions.items().get(0).getAmount().value()).isEqualTo(40);
    }

    @Test
    void credit_aQuizAndAMiniProject_addBothRewards() {
        credit(2, 10, ResolvedCaseType.QUIZ);
        credit(2, 11, ResolvedCaseType.MINI_PROJECT);

        assertThat(wallets.items().get(0).getBalance()).isEqualTo(65);
        assertThat(transactions.items()).extracting(movement -> movement.getAmount().value()).containsExactly(25, 40);
    }

    @Test
    void credit_withoutCaseType_failsAndChangesNothing() {
        assertFailure(credit(2, 10, null), RecognitionIncentivesError.INTERNAL_SERVER_ERROR);
        assertThat(wallets.items()).isEmpty();
        assertThat(transactions.items()).isEmpty();
    }

    @Test
    void credit_aVerifierWithWallet_addsToTheBalanceReadingItWithALock() {
        Wallet existing = walletWith(2, 20);

        Result<Wallet> result = credit(2, 10);

        assertThat(result.value()).isSameAs(existing);
        assertThat(existing.getBalance()).isEqualTo(45);
        assertThat(wallets.lockedReads()).isEqualTo(1);
    }

    @Test
    void credit_theSameCaseTwice_paysOnlyOnce() {
        credit(2, 10);

        Result<Wallet> again = credit(2, 10);

        assertThat(again.isSuccess()).isTrue();
        assertThat(again.value().getBalance()).isEqualTo(25);
        assertThat(transactions.items()).hasSize(1);
    }

    @Test
    void credit_differentCases_payEachOne() {
        credit(2, 10);
        credit(2, 11);

        assertThat(wallets.items().get(0).getBalance()).isEqualTo(50);
        assertThat(transactions.items()).hasSize(2);
    }

    @Test
    void credit_theSameCaseToAnotherVerifier_paysThemToo() {
        credit(2, 10);
        credit(3, 10);

        assertThat(wallets.items()).hasSize(2);
        assertThat(transactions.items()).hasSize(2);
    }

    @Test
    void credit_whenTheDatabaseFails_returnsDatabaseError() {
        transactions.failOnSave(new DataIntegrityViolationException("boom"));

        assertFailure(credit(2, 10), RecognitionIncentivesError.DATABASE_ERROR);
    }

    @Test
    void credit_whenTheBalanceWouldOverflow_returnsInternalServerError() {
        walletWith(2, Integer.MAX_VALUE);

        assertFailure(credit(2, 10), RecognitionIncentivesError.INTERNAL_SERVER_ERROR);
    }

    // ---------- Redeem ----------

    @Test
    void redeem_withEnoughBalance_debitsTheCostAndRecordsTheMovement() {
        Wallet wallet = walletWith(2, 120);

        Result<CreditTransaction> result = redeem(2, RedemptionItem.CONTRIBUTION_CERTIFICATE);

        assertThat(result.isSuccess()).isTrue();
        assertThat(wallet.getBalance()).isZero();
        CreditTransaction movement = result.value();
        assertThat(movement.getType()).isEqualTo(TransactionType.REDEEMED);
        assertThat(movement.getAmount().value()).isEqualTo(120);
        assertThat(movement.getDescription()).isEqualTo("Redeemed: contribution certificate");
        assertThat(movement.getRelatedCaseId()).isNull();
        assertThat(wallets.lockedReads()).isEqualTo(1);
    }

    @Test
    void redeem_theAdvancedPathUnlock_costsTwoHundred() {
        Wallet wallet = walletWith(2, 230);

        Result<CreditTransaction> result = redeem(2, RedemptionItem.ADVANCED_PATH_UNLOCK);

        assertThat(result.isSuccess()).isTrue();
        assertThat(wallet.getBalance()).isEqualTo(30);
        assertThat(result.value().getAmount().value()).isEqualTo(200);
    }

    @Test
    void redeem_withInsufficientBalance_failsAndChangesNothing() {
        Wallet wallet = walletWith(2, 119);

        assertFailure(redeem(2, RedemptionItem.CONTRIBUTION_CERTIFICATE),
                RecognitionIncentivesError.INSUFFICIENT_BALANCE);
        assertThat(wallet.getBalance()).isEqualTo(119);
        assertThat(transactions.items()).isEmpty();
    }

    @Test
    void redeem_theAdvancedPathUnlockWithTheOldPrice_isInsufficient() {
        walletWith(2, 199);

        assertFailure(redeem(2, RedemptionItem.ADVANCED_PATH_UNLOCK),
                RecognitionIncentivesError.INSUFFICIENT_BALANCE);
    }

    @Test
    void redeem_withoutWallet_returnsWalletNotFound() {
        assertFailure(redeem(2, RedemptionItem.CONTRIBUTION_CERTIFICATE),
                RecognitionIncentivesError.WALLET_NOT_FOUND);
    }

    @Test
    void redeem_withoutItem_returnsInvalidRedemptionItem() {
        walletWith(2, 300);

        assertFailure(redeem(2, null), RecognitionIncentivesError.INVALID_REDEMPTION_ITEM);
    }

    @Test
    void redeem_whenTheDatabaseFails_returnsDatabaseError() {
        walletWith(2, 120);
        transactions.failOnSave(new DataIntegrityViolationException("boom"));

        assertFailure(redeem(2, RedemptionItem.CONTRIBUTION_CERTIFICATE),
                RecognitionIncentivesError.DATABASE_ERROR);
    }

    @Test
    void failures_areLocalizedInTheLocaleOfTheCaller() {
        walletWith(2, 0);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));

        Result<CreditTransaction> result = redeem(2, RedemptionItem.CONTRIBUTION_CERTIFICATE);

        assertThat(result.message()).isEqualTo("Tus SkillCredits no alcanzan para canjear este beneficio.");
    }
}
