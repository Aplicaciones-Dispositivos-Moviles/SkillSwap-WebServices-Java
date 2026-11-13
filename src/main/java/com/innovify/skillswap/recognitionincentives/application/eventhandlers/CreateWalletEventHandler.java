package com.innovify.skillswap.recognitionincentives.application.eventhandlers;

import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateWalletCommand;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A new account gets its empty wallet. */
public class CreateWalletEventHandler implements DomainEventHandler<UserRegistered> {

    private static final Logger log = LoggerFactory.getLogger(CreateWalletEventHandler.class);

    private final WalletCommandService commandService;

    public CreateWalletEventHandler(WalletCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handle(UserRegistered event) {
        var result = commandService.handle(new CreateWalletCommand(event.userId()));
        if (result.isFailure()) {
            log.warn("The wallet of the user {} was not created: {}", event.userId(), result.error());
        }
    }
}
