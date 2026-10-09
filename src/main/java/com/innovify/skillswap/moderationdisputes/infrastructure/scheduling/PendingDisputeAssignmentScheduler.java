package com.innovify.skillswap.moderationdisputes.infrastructure.scheduling;

import com.innovify.skillswap.moderationdisputes.application.commandservices.DisputeCommandService;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.AssignPendingDisputesCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A suspicious certificate escalated when no verifier was available waits without a reviewer. This retries those
 * disputes periodically, so they are assigned as soon as a verifier (preferably a senior) is available. Every dispute
 * is locked while it is assigned, so several instances can run it at once.
 */
@Component
@ConditionalOnProperty(name = "moderation.assignment-retry-enabled", havingValue = "true", matchIfMissing = true)
public class PendingDisputeAssignmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingDisputeAssignmentScheduler.class);

    private final DisputeCommandService commandService;

    public PendingDisputeAssignmentScheduler(DisputeCommandService commandService) {
        this.commandService = commandService;
    }

    @Scheduled(initialDelayString = "${moderation.assignment-retry-interval:15m}",
            fixedDelayString = "${moderation.assignment-retry-interval:15m}")
    public void assignPendingDisputes() {
        var result = commandService.handle(new AssignPendingDisputesCommand());
        if (result.isFailure()) {
            log.warn("The pending disputes could not be assigned: {}", result.error());
        } else if (result.value() > 0) {
            log.info("{} pending disputes were assigned to a reviewer", result.value());
        }
    }
}
