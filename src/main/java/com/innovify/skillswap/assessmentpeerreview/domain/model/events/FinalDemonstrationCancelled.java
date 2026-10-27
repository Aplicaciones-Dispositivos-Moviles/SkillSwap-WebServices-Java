package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * A student cancelled a scheduled final demonstration (US28).
 *
 * @param demonstrationId the demonstration
 * @param studentId       the student
 * @param verifierUserId  the verifier who was assigned
 */
public record FinalDemonstrationCancelled(int demonstrationId, int studentId, int verifierUserId)
        implements DomainEvent {
}
