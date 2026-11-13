package com.innovify.skillswap.recognitionincentives.application.internal.commandservices;

import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateWalletCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreditVerifierForDemonstrationCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.RedeemCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.entities.RedemptionIdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.model.events.AdvancedPathUnlockRedeemed;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.ResolvedCaseType;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.RedemptionIdempotencyKeyRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.recognitionincentives.domain.services.CreditRewards;
import com.innovify.skillswap.recognitionincentives.domain.services.RedemptionPricing;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Wallet command service. Not {@code @Transactional}: the wallet and its movement are saved together in the
 * given {@link TransactionOperations}, which must start a new transaction because the credit commands come from
 * event handlers that run after the commit of the transaction that raised the event. The wallet is read with a
 * lock inside that transaction, so two movements of the same wallet cannot read the same balance.
 *
 * <p>A redemption can carry the Idempotency-Key of the attempt: the key is stored with the movement in the same
 * transaction, and a retry with the same key answers that movement without charging again (nor announcing it again).
 *
 * <p>A redeemed advanced path unlock is announced with {@link AdvancedPathUnlockRedeemed} once committed, so
 * Learning Path Engine delivers the benefit; it can also read the redemptions back through the facade of this context.
 */
public class WalletCommandServiceImpl implements WalletCommandService {

    static final String EARNED_DESCRIPTION = "Verification case resolved";
    static final String DEMONSTRATION_EARNED_DESCRIPTION = "Final demonstration reviewed";
    static final String REDEEMED_DESCRIPTION_PREFIX = "Redeemed: ";

    private static final Logger log = LoggerFactory.getLogger(WalletCommandServiceImpl.class);

    private final WalletRepository wallets;
    private final CreditTransactionRepository transactions;
    private final RedemptionIdempotencyKeyRepository idempotencyKeys;
    private final RedemptionPricing pricing;
    private final TransactionOperations transactionOperations;
    private final DomainEventPublisher eventPublisher;
    private final RecognitionIncentivesFailures failures;

    public WalletCommandServiceImpl(WalletRepository wallets, CreditTransactionRepository transactions,
                                    RedemptionIdempotencyKeyRepository idempotencyKeys, RedemptionPricing pricing,
                                    TransactionOperations transactionOperations,
                                    DomainEventPublisher eventPublisher, MessageSource messageSource) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.idempotencyKeys = idempotencyKeys;
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
    public Result<Wallet> handle(CreditVerifierForDemonstrationCommand command) {
        try {
            Credits amount = CreditRewards.forResolvedCase(ResolvedCaseType.FINAL_DEMONSTRATION);
            Wallet credited = transactionOperations.execute(status -> {
                Optional<Wallet> existing = wallets.findByOwnerIdForUpdate(command.verifierUserId());
                if (existing.isPresent()
                        && transactions.existsEarnedForDemonstration(existing.get().getId(), command.demonstrationId())) {
                    return existing.get();
                }

                // The movement needs the id of the wallet, so a new wallet is saved first.
                Wallet wallet = existing.orElseGet(() -> wallets.save(new Wallet(command.verifierUserId())));
                wallet.credit(amount);
                Wallet saved = wallets.save(wallet);
                transactions.save(CreditTransaction.earnedForDemonstration(saved.getId(), amount,
                        DEMONSTRATION_EARNED_DESCRIPTION, command.demonstrationId()));
                return saved;
            });
            return Result.success(credited);
        } catch (RuntimeException exception) {
            log.error("Could not credit the verifier {} for the demonstration {}", command.verifierUserId(),
                    command.demonstrationId(), exception);
            return failures.failure(RecognitionIncentivesFailures.toError(exception));
        }
    }

    @Override
    public Result<CreditTransaction> handle(RedeemCommand command) {
        if (command.item() == null) {
            return failures.failure(RecognitionIncentivesError.INVALID_REDEMPTION_ITEM);
        }

        try {
            Redemption redemption = transactionOperations.execute(status -> redeem(command));
            Result<CreditTransaction> result = redemption.result();
            if (redemption.replayed() || result.isFailure()) {
                return result;
            }

            if (command.item() == RedemptionItem.ADVANCED_PATH_UNLOCK) {
                eventPublisher.publish(new AdvancedPathUnlockRedeemed(command.userId(), result.value().getId()));
            }
            if (command.idempotencyKey() != null) {
                // The movement as stored (the database keeps microseconds), so a retry answers exactly the same.
                return transactions.findById(result.value().getId()).map(Result::success).orElse(result);
            }
            return result;
        } catch (DataIntegrityViolationException exception) {
            // Another request with the same key stored it first: its movement is the answer of this one too.
            Optional<CreditTransaction> stored = command.idempotencyKey() == null
                    ? Optional.empty()
                    : storedMovement(command);
            if (stored.isPresent()) {
                return Result.success(stored.get());
            }
            log.error("Could not redeem a benefit for the user {}", command.userId(), exception);
            return failures.failure(RecognitionIncentivesFailures.toError(exception));
        } catch (RuntimeException exception) {
            log.error("Could not redeem a benefit for the user {}", command.userId(), exception);
            return failures.failure(RecognitionIncentivesFailures.toError(exception));
        }
    }

    /**
     * Runs inside the transaction. The wallet is locked first, so a retry with the same key waits for the first
     * request and then finds its key; the key is stored with the movement, so a failed redemption stores none.
     */
    private Redemption redeem(RedeemCommand command) {
        Optional<Wallet> existing = wallets.findByOwnerIdForUpdate(command.userId());
        if (command.idempotencyKey() != null) {
            Optional<CreditTransaction> stored = storedMovement(command);
            if (stored.isPresent()) {
                return Redemption.replayed(stored.get());
            }
        }
        if (existing.isEmpty()) {
            return Redemption.of(failures.failure(RecognitionIncentivesError.WALLET_NOT_FOUND));
        }

        Wallet wallet = existing.get();
        // The wallet is locked, so two simultaneous redemptions of the badge cannot both pass this check.
        if (command.item() == RedemptionItem.BADGE
                && !transactions.findRedemptionIds(wallet.getId(), RedemptionItem.BADGE).isEmpty()) {
            return Redemption.of(failures.failure(RecognitionIncentivesError.BENEFIT_ALREADY_REDEEMED));
        }
        Credits cost = pricing.calculateCost(command.item());
        if (!wallet.canAfford(cost)) {
            return Redemption.of(failures.failure(RecognitionIncentivesError.INSUFFICIENT_BALANCE));
        }

        wallet.debit(cost);
        wallets.save(wallet);
        CreditTransaction movement = transactions.save(new CreditTransaction(wallet.getId(), cost,
                TransactionType.REDEEMED, REDEEMED_DESCRIPTION_PREFIX + command.item().description(), null,
                command.item()));
        if (command.idempotencyKey() != null) {
            // Fails on a key another request stored meanwhile, which rolls back this debit.
            idempotencyKeys.save(new RedemptionIdempotencyKey(command.userId(), command.idempotencyKey(),
                    movement.getId()));
        }
        return Redemption.of(Result.success(movement));
    }

    /** The movement recorded by the redemption that stored the key of the command, if any. */
    private Optional<CreditTransaction> storedMovement(RedeemCommand command) {
        return idempotencyKeys.findByUserIdAndKey(command.userId(), command.idempotencyKey())
                .flatMap(key -> transactions.findById(key.getCreditTransactionId()));
    }

    /** The outcome of a redemption; a replayed one answers the movement of an earlier request with the same key. */
    private record Redemption(Result<CreditTransaction> result, boolean replayed) {

        static Redemption of(Result<CreditTransaction> result) {
            return new Redemption(result, false);
        }

        static Redemption replayed(CreditTransaction stored) {
            return new Redemption(Result.success(stored), true);
        }
    }
}
