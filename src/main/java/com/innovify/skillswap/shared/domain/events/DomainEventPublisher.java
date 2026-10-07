package com.innovify.skillswap.shared.domain.events;

/**
 * Publishes domain events in process. It must be called once the change is saved: inside a transaction
 * the handlers run after the commit. A handler failure never reaches the caller, it is only logged.
 */
public interface DomainEventPublisher {

    <E extends DomainEvent> void publish(E event);
}
