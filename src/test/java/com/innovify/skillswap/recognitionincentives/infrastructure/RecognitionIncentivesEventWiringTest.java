package com.innovify.skillswap.recognitionincentives.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.RedeemCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The events of other contexts reach the wallet handlers, and two redemptions never spend the same credits. */
class RecognitionIncentivesEventWiringTest extends PostgresIntegrationTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private WalletRepository wallets;

    @Autowired
    private CreditTransactionRepository transactions;

    @Autowired
    private WalletCommandService commandService;

    private static VerificationCaseResolved resolved(int caseId, int verifierUserId, ReviewDecision decision) {
        return resolved(caseId, verifierUserId, CaseType.QUIZ, decision);
    }

    private static VerificationCaseResolved resolved(int caseId, int verifierUserId, CaseType caseType,
                                                     ReviewDecision decision) {
        return new VerificationCaseResolved(caseId, 1, verifierUserId, 5, "http-basics", caseType, decision, null);
    }

    @Test
    void userRegistered_createsTheEmptyWallet() {
        publisher.publish(new UserRegistered(4, UserRole.STUDENT));

        Wallet wallet = wallets.findByOwnerId(4).orElseThrow();
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    void userRegistered_twice_keepsASingleWallet() throws Exception {
        publisher.publish(new UserRegistered(4, UserRole.STUDENT));
        publisher.publish(new UserRegistered(4, UserRole.STUDENT));

        assertThat(queryString("SELECT count(*) FROM wallets")).isEqualTo("1");
    }

    @Test
    void approvedQuizCase_creditsTheVerifierTwentyFiveCredits() {
        publisher.publish(resolved(10, 2, ReviewDecision.APPROVED));

        assertThat(wallets.findByOwnerId(2).orElseThrow().getBalance()).isEqualTo(25);
        assertThat(wallets.findByOwnerId(1)).isEmpty();
    }

    @Test
    void rejectedCase_alsoCreditsTheVerifier() {
        publisher.publish(resolved(10, 2, ReviewDecision.REJECTED));

        assertThat(wallets.findByOwnerId(2).orElseThrow().getBalance()).isEqualTo(25);
    }

    @Test
    void resolvedMiniProjectCase_creditsTheVerifierFortyCredits() {
        publisher.publish(resolved(10, 2, CaseType.MINI_PROJECT, ReviewDecision.REJECTED));

        Wallet wallet = wallets.findByOwnerId(2).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(40);
        assertThat(transactions.findByWalletId(wallet.getId())).extracting(movement -> movement.getAmount().value())
                .containsExactly(40);
    }

    @Test
    void sameCaseTwice_paysOnlyOnce() {
        publisher.publish(resolved(10, 2, ReviewDecision.APPROVED));
        publisher.publish(resolved(10, 2, ReviewDecision.APPROVED));

        Wallet wallet = wallets.findByOwnerId(2).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(25);
        assertThat(transactions.findByWalletId(wallet.getId())).hasSize(1);
    }

    @Test
    void creditsOfSeveralCases_addUpOnTheExistingWallet() {
        publisher.publish(new UserRegistered(2, UserRole.STUDENT));
        publisher.publish(resolved(10, 2, ReviewDecision.APPROVED));
        publisher.publish(resolved(11, 2, CaseType.MINI_PROJECT, ReviewDecision.REJECTED));

        Wallet wallet = wallets.findByOwnerId(2).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(65);
        assertThat(transactions.findByWalletId(wallet.getId())).hasSize(2);
    }

    @Test
    void twoRedemptionsAtTheSameTime_spendTheCreditsOnlyOnce() throws Exception {
        Wallet wallet = wallets.save(new Wallet(2).credit(new Credits(120)));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result<CreditTransaction>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return commandService.handle(new RedeemCommand(2, RedemptionItem.CONTRIBUTION_CERTIFICATE));
                }));
            }
            start.countDown();

            List<Result<CreditTransaction>> results = new ArrayList<>();
            for (Future<Result<CreditTransaction>> future : futures) {
                results.add(future.get());
            }

            assertThat(results.stream().filter(Result::isSuccess)).hasSize(1);
            assertThat(results.stream().filter(Result::isFailure).map(Result::error))
                    .containsExactly(RecognitionIncentivesError.INSUFFICIENT_BALANCE);
        } finally {
            executor.shutdownNow();
        }
        assertThat(wallets.findByOwnerId(2).orElseThrow().getBalance()).isZero();
        assertThat(transactions.findByWalletId(wallet.getId())).hasSize(1);
    }
}
