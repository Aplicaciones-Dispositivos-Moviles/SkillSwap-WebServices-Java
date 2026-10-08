package com.innovify.skillswap.shared.domain.events;

/** Reacts to a domain event published by another bounded context. */
public interface DomainEventHandler<E extends DomainEvent> {

    void handle(E event);
}
