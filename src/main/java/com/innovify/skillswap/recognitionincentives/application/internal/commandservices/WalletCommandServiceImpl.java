package com.innovify.skillswap.recognitionincentives.application.internal.commandservices;

import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateWalletCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.RedeemCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.events.AdvancedPathUnlockRedeemed;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.recognitionincentives.domain.services.CreditRewards;
import com.innovify.skillswap.recognitionincentives.domain.services.RedemptionPricing;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Wallet command service. Not {@code @Transactional}: the wallet and its movement are saved together in the
 * given {@link TransactionOperations}, which must start a new transaction because the credit commands come from
 * event handlers that run after the commit of the transaction that raised the event. The wallet is read with a
 * lock inside that transaction, so two movements of the same wallet cannot read the same balance.
 *
 * <p>A redeemed advanced path unlock is announced with {@link AdvancedPathUnlockRedeemed} once committed, so
 * Learning Path Engine delivers the benefit; it can also read the redemptions back through the facade of this context.
 */
public class WalletCommandServiceImpl implements WalletCommandService {

    static final String EARNED_DESCRIPTION = "Verification case resolved";
    static final String REDEEMED_DESCRIPTION_PREFIX = "Redeemed: ";

    private static final Logger log = LoggerFactory.getLogger(WalletCommandServiceImpl.class);

    private final WalletRepository wallets;
    private final CreditTransactionRepository transactions;
    private final RedemptionPricing pricing;
    private final TransactionOperations transactionOperations;
    private final DomainEventPublisher eventPublisher;
    private final RecognitionIncentivesFailures failures;

    public WalletCommandServiceImpl(WalletRepository wallets, CreditTransactionRepository transactions,
                                    RedemptionPricing pricing, TransactionOperations transactionOperations,
                                    DomainEventPublisher eventPublisher, MessageSource messageSource) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.pricing = pricing;
        this.transactionOperations = transactionOperations;
        this.eventPublisher = eventPublisher;
        this.failures = new RecognitionIncentivesFailures(messageSource);
    }

    @Override
    public Result<Wallet> handle(CreateWalletCommand command) {
        try {
            Optional<Wallet> existing = wallets.findByOwnerId(command.ownerId());
            if (existing.isPresent()) {
                return Result.success(existing.get());
            }
            return Result.success(wallets.save(new Wallet(command.ownerId())));
        } catch (RuntimeException exception) {
            log.error("Could not create the wallet of the user {}", command.ownerId(), exception);
            return failures.failure(RecognitionIncentivesFailures.toError(exception));
        }
    }

    @Override
    public Result<Wallet> handle(CreditVerifierCommand command) {
        try {
            Credits amount = CreditRewards.forResolvedCase(command.caseType());
            Wallet credited = transactionOperations.execute(status -> {
                Optional<Wallet> existing = wallets.findByOwnerIdForUpdate(command.verifierUserId());
                if (existing.isPresent() && transactions.existsEarnedForCase(existing.get().getId(), command.caseId())) {
                    return existing.get();
                }

                // The movement needs the id of the wallet, so a new wallet is saved first.
                Wallet wallet = existing.orElseGet(() -> wallets.save(new Wallet(command.verifierUserId())));
                wallet.credit(amount);
                Wallet saved = wallets.save(wallet);
                transactions.save(new CreditTransaction(saved.getId(), amount, TransactionType.EARNED,
                        EARNED_DESCRIPTION, command.caseId()));
                return saved;
            });
            return Result.success(credited);
        } catch (RuntimeException exception) {
            log.error("Could not credit the verifier {}", command.verifierUserId(), exception);
            return failures.failure(RecognitionIncentivesFailures.toError(exception));
        }
    }

    @Override
    public Result<CreditTransaction> handle(RedeemCommand command) {
        if (command.item() == null) {
            return failures.failure(RecognitionIncentivesError.INVALID_REDEMPTION_ITEM);
        }

        try {
            Result<CreditTransaction> result = transactionOperations.execute(status -> {
                Optional<Wallet> existing = wallets.findByOwnerIdForUpdate(command.userId());
                if (existing.isEmpty()) {
                    return failures.<CreditTransaction>failure(RecognitionIncentivesError.WALLET_NOT_FOUND);
                }

                Wallet wallet = existing.get();
                Credits cost = pricing.calculateCost(command.item());
                if (!wallet.canAfford(cost)) {
                    return failures.<CreditTransaction>failure(RecognitionIncentivesError.INSUFFICIENT_BALANCE);
                }

                wallet.debit(cost);
                wallets.save(wallet);
                CreditTransaction movement = transactions.save(new CreditTransaction(wallet.getId(), cost,
                        TransactionType.REDEEMED, REDEEMED_DESCRIPTION_PREFIX + command.item().description(), null,
                        command.item()));
                return Result.success(movement);
            });

            if (result.isSuccess() && command.item() == RedemptionItem.ADVANCED_PATH_UNLOCK) {
                eventPublisher.publish(new AdvancedPathUnlockRedeemed(command.userId(), result.value().getId()));
            }
            return result;
        } catch (RuntimeException exception) {
            log.error("Could not redeem a benefit for the user {}", command.userId(), exception);
            return failures.failure(RecognitionIncentivesFailures.toError(exception));
        }
    }
}
