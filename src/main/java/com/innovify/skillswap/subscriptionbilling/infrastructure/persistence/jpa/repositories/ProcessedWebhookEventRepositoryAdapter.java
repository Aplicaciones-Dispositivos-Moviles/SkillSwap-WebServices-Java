package com.innovify.skillswap.subscriptionbilling.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.subscriptionbilling.domain.model.entities.ProcessedWebhookEvent;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.ProcessedWebhookEventRepository;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link ProcessedWebhookEventRepository} port on top of Spring Data JPA. */
@Repository
public class ProcessedWebhookEventRepositoryAdapter implements ProcessedWebhookEventRepository {

    private final ProcessedWebhookEventJpaRepository jpaRepository;

    public ProcessedWebhookEventRepositoryAdapter(ProcessedWebhookEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ProcessedWebhookEvent save(ProcessedWebhookEvent event) {
        return jpaRepository.saveAndFlush(event);
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }
}
