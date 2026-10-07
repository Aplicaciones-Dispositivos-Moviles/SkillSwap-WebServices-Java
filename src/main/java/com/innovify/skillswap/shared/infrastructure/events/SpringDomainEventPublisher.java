package com.innovify.skillswap.shared.infrastructure.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * In-process publisher. It resolves the handlers registered for the event and runs them one after another;
 * a failing handler is logged and never stops the others or the caller. Inside a transaction the handlers
 * run after the commit, so a handler that writes to the database must open its own transaction
 * (REQUIRES_NEW).
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventPublisher.class);

    private final ApplicationContext context;

    public SpringDomainEventPublisher(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public <E extends DomainEvent> void publish(E event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(event);
                }
            });
        } else {
            dispatch(event);
        }
    }

    private <E extends DomainEvent> void dispatch(E event) {
        ObjectProvider<DomainEventHandler<E>> handlers = context.getBeanProvider(
                ResolvableType.forClassWithGenerics(DomainEventHandler.class, event.getClass()));

        handlers.orderedStream().forEach(handler -> {
            try {
                handler.handle(event);
            } catch (Exception exception) {
                log.error("Handler {} failed for the event {}",
                        handler.getClass().getSimpleName(), event.getClass().getSimpleName(), exception);
            }
        });
    }
}
