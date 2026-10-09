package com.innovify.skillswap.credentialverification.application.fakes;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.util.ArrayList;
import java.util.List;

/** Records the published events instead of dispatching them. */
public class FakeDomainEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    public List<DomainEvent> published() {
        return published;
    }

    @Override
    public <E extends DomainEvent> void publish(E event) {
        published.add(event);
    }
}
