package com.innovify.skillswap.subscriptionbilling.domain.repositories;

import com.innovify.skillswap.subscriptionbilling.domain.model.entities.ProcessedWebhookEvent;

/** Persistence port of the {@link ProcessedWebhookEvent} entity. */
public interface ProcessedWebhookEventRepository {

    /**
     * Records the event and flushes right away.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException when the event was already recorded
     */
    ProcessedWebhookEvent save(ProcessedWebhookEvent event);

    boolean existsByEventId(String eventId);
}
