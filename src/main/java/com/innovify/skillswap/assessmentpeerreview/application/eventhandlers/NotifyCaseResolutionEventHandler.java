package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.util.Map;
import org.springframework.context.MessageSource;

/** The student is told when a verifier approved or rejected their quiz or mini-project. */
public class NotifyCaseResolutionEventHandler implements DomainEventHandler<VerificationCaseResolved> {

    public static final String NOTIFICATION_TYPE = "VerificationCaseResolved";

    private final StudentPushNotifier notifier;

    public NotifyCaseResolutionEventHandler(UserNotificationsContextFacade notifications,
                                            MessageSource messageSource) {
        this.notifier = new StudentPushNotifier(notifications, messageSource);
    }

    @Override
    public void handle(VerificationCaseResolved event) {
        boolean approved = event.decision() == ReviewDecision.APPROVED;
        String caseType = notifier.message(event.caseType() == CaseType.QUIZ
                ? "CaseResolvedPushTypeQuiz" : "CaseResolvedPushTypeMiniProject");
        notifier.send(event.studentId(), NOTIFICATION_TYPE,
                approved ? "CaseApprovedPushTitle" : "CaseRejectedPushTitle",
                approved ? "CaseApprovedPushBody" : "CaseRejectedPushBody", Map.of(
                        "caseId", String.valueOf(event.caseId()),
                        "pathNodeId", String.valueOf(event.pathNodeId()),
                        "skillTag", event.skillTag(),
                        "caseType", event.caseType().value(),
                        "decision", event.decision().value()),
                StudentPushNotifier.skillName(event.skillTag()), caseType);
    }
}
