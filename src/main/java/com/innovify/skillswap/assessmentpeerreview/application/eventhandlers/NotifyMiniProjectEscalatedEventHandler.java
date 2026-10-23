package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectEscalatedByAi;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.util.Map;
import org.springframework.context.MessageSource;

/** US19: the student is told when the AI could not approve the delivery on its own and a verifier will review it. */
public class NotifyMiniProjectEscalatedEventHandler implements DomainEventHandler<MiniProjectEscalatedByAi> {

    public static final String NOTIFICATION_TYPE = "MiniProjectEscalatedByAi";

    private final StudentPushNotifier notifier;

    public NotifyMiniProjectEscalatedEventHandler(UserNotificationsContextFacade notifications,
                                                  MessageSource messageSource) {
        this.notifier = new StudentPushNotifier(notifications, messageSource);
    }

    @Override
    public void handle(MiniProjectEscalatedByAi event) {
        notifier.send(event.studentId(), NOTIFICATION_TYPE, "MiniProjectEscalatedPushTitle",
                "MiniProjectEscalatedPushBody", Map.of(
                        "submissionId", String.valueOf(event.submissionId()),
                        "caseId", String.valueOf(event.verificationCaseId()),
                        "pathNodeId", String.valueOf(event.pathNodeId()),
                        "skillTag", event.skillTag()),
                StudentPushNotifier.skillName(event.skillTag()));
    }
}
