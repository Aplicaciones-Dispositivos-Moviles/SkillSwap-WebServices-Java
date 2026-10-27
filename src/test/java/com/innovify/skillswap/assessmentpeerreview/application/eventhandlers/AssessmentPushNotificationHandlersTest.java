package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationReviewed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectApprovedByAi;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectEscalatedByAi;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.iam.application.acl.PushNotificationOutcome;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class AssessmentPushNotificationHandlersTest {

    private record Sent(int userId, String title, String body, Map<String, String> data) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private PushNotificationOutcome outcome = PushNotificationOutcome.SENT;
    private final UserNotificationsContextFacade facade = (userId, title, body, data) -> {
        sent.add(new Sent(userId, title, body, data));
        return outcome;
    };
    private final ResourceBundleMessageSource messages = messages();

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    @Test
    void miniProjectApprovedByTheAi_notifiesTheStudentInSpanish() {
        new NotifyMiniProjectApprovedEventHandler(facade, messages)
                .handle(new MiniProjectApprovedByAi(5, 7, 10, "rest-api-design"));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.userId()).isEqualTo(7);
            assertThat(push.title()).isEqualTo("¡Miniproyecto aprobado!");
            assertThat(push.body()).isEqualTo("La IA aprobó tu entrega de rest api design y completó el nodo.");
            assertThat(push.data()).containsEntry("type", "MiniProjectApprovedByAi")
                    .containsEntry("submissionId", "5").containsEntry("pathNodeId", "10")
                    .containsEntry("skillTag", "rest-api-design");
        });
    }

    @Test
    void miniProjectEscalatedByTheAi_tellsTheStudentAVerifierWillReviewIt() {
        new NotifyMiniProjectEscalatedEventHandler(facade, messages)
                .handle(new MiniProjectEscalatedByAi(5, 7, 10, "oop", 33));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.userId()).isEqualTo(7);
            assertThat(push.title()).isEqualTo("Tu entrega será revisada");
            assertThat(push.body()).isEqualTo(
                    "La IA no pudo aprobar tu entrega de oop por sí sola. Un Verificador la revisará.");
            assertThat(push.data()).containsEntry("type", "MiniProjectEscalatedByAi")
                    .containsEntry("caseId", "33").containsEntry("submissionId", "5");
        });
    }

    @Test
    void anApprovedQuizCase_notifiesTheStudent() {
        new NotifyCaseResolutionEventHandler(facade, messages).handle(new VerificationCaseResolved(33, 7, 9, 10,
                "sql-fundamentals", CaseType.QUIZ, ReviewDecision.APPROVED, null));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.userId()).isEqualTo(7);
            assertThat(push.title()).isEqualTo("Caso aprobado");
            assertThat(push.body()).isEqualTo("Un Verificador aprobó tu quiz de sql fundamentals.");
            assertThat(push.data()).containsEntry("type", "VerificationCaseResolved")
                    .containsEntry("caseId", "33").containsEntry("caseType", "Quiz")
                    .containsEntry("decision", "Approved");
        });
    }

    @Test
    void aRejectedMiniProjectCase_notifiesTheStudentWithoutRevealingTheVerifier() {
        new NotifyCaseResolutionEventHandler(facade, messages).handle(new VerificationCaseResolved(33, 7, 9, 10,
                "oop", CaseType.MINI_PROJECT, ReviewDecision.REJECTED, null));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.title()).isEqualTo("Caso rechazado");
            assertThat(push.body()).isEqualTo("Un Verificador rechazó tu miniproyecto de oop. Revisa el detalle en la app.");
            assertThat(push.data()).containsEntry("decision", "Rejected").doesNotContainKey("verifierUserId");
            assertThat(push.data().values()).doesNotContain("9");
        });
    }

    @Test
    void whenTheStudentHasNoDeviceToken_theHandlersDoNotFail() {
        outcome = PushNotificationOutcome.NO_DEVICE_TOKEN;

        new NotifyMiniProjectApprovedEventHandler(facade, messages)
                .handle(new MiniProjectApprovedByAi(5, 7, 10, "oop"));
        new NotifyMiniProjectEscalatedEventHandler(facade, messages)
                .handle(new MiniProjectEscalatedByAi(5, 7, 10, "oop", 33));

        assertThat(sent).hasSize(2);
    }

    @Test
    void anApprovedFinalDemonstration_notifiesTheStudent() {
        new NotifyFinalDemonstrationReviewedEventHandler(facade, messages)
                .handle(new FinalDemonstrationReviewed(4, 7, 9, "rest-api-design", 88, true));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.userId()).isEqualTo(7);
            assertThat(push.title()).isEqualTo("¡Demostración final aprobada!");
            assertThat(push.body()).isEqualTo("Un Verificador aprobó tu demostración final de rest api design.");
            assertThat(push.data()).containsEntry("type", "FinalDemonstrationApproved")
                    .containsEntry("demonstrationId", "4").containsEntry("skillTag", "rest-api-design")
                    .containsEntry("score", "88");
        });
    }

    @Test
    void aRejectedFinalDemonstration_notifiesTheStudent() {
        new NotifyFinalDemonstrationReviewedEventHandler(facade, messages)
                .handle(new FinalDemonstrationReviewed(4, 7, 9, "oop", 40, false));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.title()).isEqualTo("Demostración final no aprobada");
            assertThat(push.body()).isEqualTo(
                    "Un Verificador no aprobó tu demostración final de oop. Revisa el detalle en la app.");
            assertThat(push.data()).containsEntry("type", "FinalDemonstrationRejected")
                    .containsEntry("demonstrationId", "4").containsEntry("score", "40");
        });
    }
}
