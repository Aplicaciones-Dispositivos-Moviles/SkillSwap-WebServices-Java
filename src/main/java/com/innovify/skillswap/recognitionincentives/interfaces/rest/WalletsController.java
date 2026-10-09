package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.recognitionincentives.application.queryservices.WalletQueryService;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletByOwnerIdQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetWalletTransactionsQuery;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesResourceAssemblers;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The SkillCredits wallet of a user. The credits are internal and not monetary: they are earned by resolving
 * verification cases and can only be spent on benefits of the platform. Only the owner can read their wallet.
 */
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletsController {

    private final WalletQueryService queryService;
    private final MessageSource messageSource;

    public WalletsController(WalletQueryService queryService, MessageSource messageSource) {
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /**
     * The balance of the wallet. 200, 403 (not the caller's) or 404 (accounts that existed before the wallets
     * get theirs with the first credit).
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getByUserId(@PathVariable int userId, @AuthenticationPrincipal User actor) {
        if (actor.getId() != userId) {
            return RecognitionIncentivesErrorResponses.of(messageSource, RecognitionIncentivesError.NOT_WALLET_OWNER);
        }
        var wallet = queryService.handle(new GetWalletByOwnerIdQuery(userId));
        if (wallet.isEmpty()) {
            return RecognitionIncentivesErrorResponses.of(messageSource, RecognitionIncentivesError.WALLET_NOT_FOUND);
        }
        return ResponseEntity.ok(RecognitionIncentivesResourceAssemblers.toResource(wallet.get()));
    }

    /** The movements of the wallet, from the most recent to the oldest. 200, 403 or 404. */
    @GetMapping("/{userId}/transactions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getTransactionsByUserId(@PathVariable int userId,
                                                     @AuthenticationPrincipal User actor) {
        if (actor.getId() != userId) {
            return RecognitionIncentivesErrorResponses.of(messageSource, RecognitionIncentivesError.NOT_WALLET_OWNER);
        }
        var transactions = queryService.handle(new GetWalletTransactionsQuery(userId));
        if (transactions.isEmpty()) {
            return RecognitionIncentivesErrorResponses.of(messageSource, RecognitionIncentivesError.WALLET_NOT_FOUND);
        }
        return ResponseEntity.ok(transactions.get().stream()
                .map(RecognitionIncentivesResourceAssemblers::toResource)
                .toList());
    }
}
