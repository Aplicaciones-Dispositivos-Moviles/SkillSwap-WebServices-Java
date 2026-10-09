package com.innovify.skillswap.credentialverification.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.credentialverification.domain.model.events.CertificateVerificationResolved;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.iam.application.acl.PushNotificationOutcome;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

class NotifyCertificateResolutionEventHandlerTest {

    private record Sent(int userId, String title, String body, Map<String, String> data) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private final UserNotificationsContextFacade facade = (userId, title, body, data) -> {
        sent.add(new Sent(userId, title, body, data));
        return PushNotificationOutcome.SENT;
    };
    private final NotifyCertificateResolutionEventHandler handler = new NotifyCertificateResolutionEventHandler(
            facade, messages());

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    @Test
    void verified_notifiesTheOwnerInSpanish() {
        handler.handle(new CertificateVerificationResolved(12, 7, VerificationStatus.VERIFIED, "Java Básico", null));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.userId()).isEqualTo(7);
            assertThat(push.title()).isEqualTo("Certificado verificado");
            assertThat(push.body()).isEqualTo("Tu certificado \"Java Básico\" fue verificado.");
            assertThat(push.data()).containsEntry("type", "CertificateVerificationResolved")
                    .containsEntry("certificateId", "12")
                    .containsEntry("status", "Verified");
        });
    }

    @Test
    void rejected_includesTheReason() {
        handler.handle(new CertificateVerificationResolved(12, 7, VerificationStatus.REJECTED, "Java Básico",
                "La firma del emisor no coincide"));

        assertThat(sent).singleElement().satisfies(push -> {
            assertThat(push.title()).isEqualTo("Certificado rechazado");
            assertThat(push.body()).isEqualTo(
                    "Tu certificado \"Java Básico\" fue rechazado. Motivo: La firma del emisor no coincide");
            assertThat(push.data()).containsEntry("status", "Rejected");
        });
    }

    @Test
    void rejectedWithoutAReasonOrCourse_saysSo() {
        handler.handle(new CertificateVerificationResolved(12, 7, VerificationStatus.REJECTED, null, null));

        assertThat(sent.get(0).body()).isEqualTo("Tu certificado #12 fue rechazado. Motivo: No se indicó un motivo.");
    }

    @Test
    void aReasonWithQuotesOrBraces_isKeptAsItIs() {
        handler.handle(new CertificateVerificationResolved(12, 7, VerificationStatus.REJECTED, "C's {intro}",
                "It's not {valid}"));

        assertThat(sent.get(0).body()).isEqualTo("Tu certificado \"C's {intro}\" fue rechazado. Motivo: It's not {valid}");
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"PENDING", "UNVERIFIED", "SUSPICIOUS"})
    void nonFinalStatuses_areNotNotified(VerificationStatus status) {
        handler.handle(new CertificateVerificationResolved(12, 7, status, "Java", null));

        assertThat(sent).isEmpty();
    }
}
