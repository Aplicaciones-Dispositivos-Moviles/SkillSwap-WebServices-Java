package com.innovify.skillswap.iam.application.fakes;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.util.ArrayList;
import java.util.List;

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
