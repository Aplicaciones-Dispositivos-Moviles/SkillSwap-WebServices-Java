package com.innovify.skillswap.subscriptionbilling.application.fakes;

import com.innovify.skillswap.subscriptionbilling.domain.model.entities.ProcessedWebhookEvent;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.ProcessedWebhookEventRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that, like the unique index of the table, refuses an event id twice. */
public class FakeProcessedWebhookEventRepository implements ProcessedWebhookEventRepository {

    private final List<ProcessedWebhookEvent> items = new ArrayList<>();
    private int nextId = 1;

    public List<ProcessedWebhookEvent> items() {
        return items;
    }

    @Override
    public ProcessedWebhookEvent save(ProcessedWebhookEvent event) {
        if (existsByEventId(event.getEventId())) {
            throw new DataIntegrityViolationException("ux_processed_webhook_events_event_id");
        }
        ReflectionTestUtils.setField(event, "id", nextId++);
        items.add(event);
        return event;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return items.stream().anyMatch(e -> e.getEventId().equals(eventId));
    }
}
