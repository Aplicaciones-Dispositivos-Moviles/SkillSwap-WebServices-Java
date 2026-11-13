package com.innovify.skillswap.credentialverification.application.eventhandlers;

import com.innovify.skillswap.credentialverification.domain.model.events.CertificateVerificationResolved;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.iam.application.acl.PushNotificationOutcome;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

/**
 * US16: when a certificate is verified or rejected, the student receives a push notification on their device
 * (Firebase Cloud Messaging), with the reason when it was rejected. When the student did not grant the notification
 * permission (no device token) nothing is sent; the new status is still returned by the certificate queries.
 *
 * <p>The notification is written in Latin American Spanish, the language of the students: it is sent after the
 * request, so there is no Accept-Language to follow.
 */
public class NotifyCertificateResolutionEventHandler implements DomainEventHandler<CertificateVerificationResolved> {

    private static final Logger log = LoggerFactory.getLogger(NotifyCertificateResolutionEventHandler.class);

    static final Locale NOTIFICATION_LOCALE = Locale.forLanguageTag("es-419");
    public static final String NOTIFICATION_TYPE = "CertificateVerificationResolved";

    private final UserNotificationsContextFacade notifications;
    private final MessageSource messageSource;

    public NotifyCertificateResolutionEventHandler(UserNotificationsContextFacade notifications,
                                                   MessageSource messageSource) {
        this.notifications = notifications;
        this.messageSource = messageSource;
    }

    @Override
    public void handle(CertificateVerificationResolved event) {
        if (event.status() != VerificationStatus.VERIFIED && event.status() != VerificationStatus.REJECTED) {
            return;
        }

        String certificate = event.courseName() == null
                ? message("CertificatePushUnnamedCertificate", event.certificateId())
                : "\"" + event.courseName() + "\"";
        String title;
        String body;
        if (event.status() == VerificationStatus.VERIFIED) {
            title = message("CertificateVerifiedPushTitle");
            body = message("CertificateVerifiedPushBody", certificate);
        } else {
            String reason = event.rejectionReason() == null
                    ? message("CertificateRejectedPushNoReason")
                    : event.rejectionReason();
            title = message("CertificateRejectedPushTitle");
            body = message("CertificateRejectedPushBody", certificate, reason);
        }

        PushNotificationOutcome outcome = notifications.sendPushNotification(event.ownerId(), title, body, Map.of(
                "type", NOTIFICATION_TYPE,
                "certificateId", String.valueOf(event.certificateId()),
                "status", event.status().value()));
        log.info("Certificate {} resolved as {}: push notification to the user {} {}", event.certificateId(),
                event.status().value(), event.ownerId(), outcome);
    }

    private String message(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, code, NOTIFICATION_LOCALE);
    }
}
