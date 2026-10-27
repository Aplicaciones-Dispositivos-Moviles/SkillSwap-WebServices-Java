package com.innovify.skillswap.assessmentpeerreview.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * The verifier reviewed the video of a final demonstration and approved or rejected it (US29).
 *
 * @param demonstrationId the demonstration
 * @param studentId       the student
 * @param verifierUserId  the verifier who reviewed it
 * @param skillTag        the skill the verifier is enabled in
 * @param score           the score given with the rubric (0 to 100)
 * @param approved        whether the score reached the passing one
 */
public record FinalDemonstrationReviewed(int demonstrationId, int studentId, int verifierUserId, String skillTag,
                                         int score, boolean approved) implements DomainEvent {
}
