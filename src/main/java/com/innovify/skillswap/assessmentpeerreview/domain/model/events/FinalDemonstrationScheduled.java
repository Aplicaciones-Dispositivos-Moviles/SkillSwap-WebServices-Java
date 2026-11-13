package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.time.Instant;

/**
 * A student scheduled the final demonstration of a completed path and a verifier was assigned (US28).
 *
 * @param demonstrationId the demonstration
 * @param studentId       the student
 * @param verifierUserId  the assigned verifier
 * @param skillTag        the skill the verifier is enabled in
 * @param scheduledAt     the moment of the demonstration
 */
public record FinalDemonstrationScheduled(int demonstrationId, int studentId, int verifierUserId, String skillTag,
                                          Instant scheduledAt) implements DomainEvent {
}
