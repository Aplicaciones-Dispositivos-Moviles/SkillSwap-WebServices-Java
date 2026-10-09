package com.innovify.skillswap.learningpathengine.application.eventhandlers;

import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GrantAdvancedPathUnlockCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.events.AdvancedPathUnlockRedeemed;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * A student redeemed an advanced path unlock with SkillCredits (US05, US32): the unlock is granted right away, so the
 * student can start the advanced path, which does not count toward the limits of the plan. The event arrives after
 * the commit of the redemption, so the unlock is saved in a transaction of its own. A lost event is recovered the
 * next time the unlocks are synchronized with the redemptions.
 */
public class GrantAdvancedPathUnlockEventHandler implements DomainEventHandler<AdvancedPathUnlockRedeemed> {

    private static final Logger log = LoggerFactory.getLogger(GrantAdvancedPathUnlockEventHandler.class);

    private final LearningPathCommandService commandService;
    private final TransactionOperations newTransaction;

    /** @param newTransaction must start a new transaction (REQUIRES_NEW) */
    public GrantAdvancedPathUnlockEventHandler(LearningPathCommandService commandService,
                                               TransactionOperations newTransaction) {
        this.commandService = commandService;
        this.newTransaction = newTransaction;
    }

    @Override
    public void handle(AdvancedPathUnlockRedeemed event) {
        var result = newTransaction.execute(status -> commandService.handle(
                new GrantAdvancedPathUnlockCommand(event.userId(), event.redemptionId())));
        if (result == null || result.isFailure()) {
            log.warn("The advanced path {} of student {} was not granted: {}", event.redemptionId(), event.userId(),
                    result == null ? null : result.error());
        }
    }
}
