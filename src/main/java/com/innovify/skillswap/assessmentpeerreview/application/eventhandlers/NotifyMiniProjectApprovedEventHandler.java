package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectApprovedByAi;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.util.Map;
import org.springframework.context.MessageSource;

/** US19: the student is told when the AI approved the delivery of the mini-project and completed the node. */
public class NotifyMiniProjectApprovedEventHandler implements DomainEventHandler<MiniProjectApprovedByAi> {

    public static final String NOTIFICATION_TYPE = "MiniProjectApprovedByAi";

    private final StudentPushNotifier notifier;

    public NotifyMiniProjectApprovedEventHandler(UserNotificationsContextFacade notifications,
                                                 MessageSource messageSource) {
        this.notifier = new StudentPushNotifier(notifications, messageSource);
    }

    @Override
    public void handle(MiniProjectApprovedByAi event) {
        notifier.send(event.studentId(), NOTIFICATION_TYPE, "MiniProjectApprovedPushTitle",
                "MiniProjectApprovedPushBody", Map.of(
                        "submissionId", String.valueOf(event.submissionId()),
                        "pathNodeId", String.valueOf(event.pathNodeId()),
                        "skillTag", event.skillTag()),
                StudentPushNotifier.skillName(event.skillTag()));
    }
}
