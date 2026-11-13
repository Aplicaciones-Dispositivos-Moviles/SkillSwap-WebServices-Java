package com.innovify.skillswap.subscriptionbilling.domain.model.entities;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A webhook notification of the payment gateway that was already applied. The gateway retries a notification
 * with the same id until it gets a 200, so recording it makes the processing idempotent: the event id is unique.
 */
@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {

    public static final int MAX_EVENT_ID_LENGTH = 255;
    public static final int MAX_EVENT_TYPE_LENGTH = 50;
    public static final int MAX_APP_USER_ID_LENGTH = 255;
    public static final int MAX_ENVIRONMENT_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "event_id", nullable = false, length = MAX_EVENT_ID_LENGTH)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = MAX_EVENT_TYPE_LENGTH)
    private String eventType;

    @Column(name = "app_user_id", length = MAX_APP_USER_ID_LENGTH)
    private String appUserId;

    @Column(name = "environment", length = MAX_ENVIRONMENT_LENGTH)
    private String environment;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /** Required by JPA. */
    protected ProcessedWebhookEvent() {
    }

    /** @throws DomainException when the id or the type are missing or too long */
    public ProcessedWebhookEvent(String eventId, String eventType, String appUserId, String environment) {
        this.eventId = required(eventId, MAX_EVENT_ID_LENGTH, "event id");
        this.eventType = required(eventType, MAX_EVENT_TYPE_LENGTH, "event type");
        this.appUserId = truncate(appUserId, MAX_APP_USER_ID_LENGTH);
        this.environment = truncate(environment, MAX_ENVIRONMENT_LENGTH);
        this.processedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public String getEnvironment() {
        return environment;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    private static String required(String value, int maxLength, String name) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new DomainException("The %s must have between 1 and %d characters.".formatted(name, maxLength));
        }
        return text;
    }

    // Informational columns: a value the gateway sends longer than expected is cut instead of losing the event.
    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
