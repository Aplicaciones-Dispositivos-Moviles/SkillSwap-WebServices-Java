package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.RedeemCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.resources.RedeemResource;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesActionResultAssembler;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesResourceAssemblers;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Spending of SkillCredits on the benefits of the platform. */
@RestController
@RequestMapping("/api/v1/credit-transactions")
public class CreditTransactionsController {

    private final WalletCommandService commandService;
    private final MessageSource messageSource;

    public CreditTransactionsController(WalletCommandService commandService, MessageSource messageSource) {
        this.commandService = commandService;
        this.messageSource = messageSource;
    }

    /**
     * Redeems a benefit: AdvancedPathUnlock costs 200 and ContributionCertificate costs 120. The cost is taken from
     * the wallet of the caller and the movement is recorded. The mobile app asks the user to confirm with the
     * biometrics of the device before calling this endpoint; the server does not check it. An AdvancedPathUnlock
     * is delivered right away as an advanced path unlock of Learning Path Engine (GET /api/v1/advanced-path-unlocks),
     * to start a path that does not count toward the limits of the plan; delivering the contribution certificate is
     * not part of this version. 201 with the movement; 400 (unknown benefit), 404 (no wallet) or
     * 409 (the balance is not enough).
     */
    @PostMapping("/redeem")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> redeem(@RequestBody RedeemResource resource, @AuthenticationPrincipal User actor) {
        RedemptionItem item = resource == null ? null : RedemptionItem.tryParse(resource.item()).orElse(null);
        if (item == null) {
            return RecognitionIncentivesErrorResponses.of(messageSource,
                    RecognitionIncentivesError.INVALID_REDEMPTION_ITEM);
        }

        var result = commandService.handle(new RedeemCommand(actor.getId(), item));
        return RecognitionIncentivesActionResultAssembler.toResponse(result,
                movement -> ResponseEntity.created(URI.create("/api/v1/wallets/" + actor.getId() + "/transactions"))
                        .body(RecognitionIncentivesResourceAssemblers.toResource(movement)));
    }
}
