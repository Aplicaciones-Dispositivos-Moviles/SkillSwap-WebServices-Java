package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationReviewed;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.util.Map;
import org.springframework.context.MessageSource;

/**
 * The student is told when a verifier approved or rejected the video of their final demonstration. The type of the
 * push is {@code FinalDemonstrationApproved} or {@code FinalDemonstrationRejected}; the app opens the demonstration
 * with the {@code demonstrationId} of the data.
 */
public class NotifyFinalDemonstrationReviewedEventHandler implements DomainEventHandler<FinalDemonstrationReviewed> {

    public static final String APPROVED_TYPE = "FinalDemonstrationApproved";
    public static final String REJECTED_TYPE = "FinalDemonstrationRejected";

    private final StudentPushNotifier notifier;

    public NotifyFinalDemonstrationReviewedEventHandler(UserNotificationsContextFacade notifications,
                                                        MessageSource messageSource) {
        this.notifier = new StudentPushNotifier(notifications, messageSource);
    }

    @Override
    public void handle(FinalDemonstrationReviewed event) {
        boolean approved = event.approved();
        notifier.send(event.studentId(), approved ? APPROVED_TYPE : REJECTED_TYPE,
                approved ? "FinalDemonstrationApprovedPushTitle" : "FinalDemonstrationRejectedPushTitle",
                approved ? "FinalDemonstrationApprovedPushBody" : "FinalDemonstrationRejectedPushBody", Map.of(
                        "demonstrationId", String.valueOf(event.demonstrationId()),
                        "skillTag", event.skillTag(),
                        "score", String.valueOf(event.score())),
                StudentPushNotifier.skillName(event.skillTag()));
    }
}
