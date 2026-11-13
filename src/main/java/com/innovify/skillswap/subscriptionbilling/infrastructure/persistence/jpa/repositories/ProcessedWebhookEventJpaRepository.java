package com.innovify.skillswap.subscriptionbilling.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.subscriptionbilling.domain.model.entities.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the "processed_webhook_events" table. Only {@link ProcessedWebhookEventRepositoryAdapter}
 * uses it.
 */
public interface ProcessedWebhookEventJpaRepository extends JpaRepository<ProcessedWebhookEvent, Integer> {

    boolean existsByEventId(String eventId);
}
