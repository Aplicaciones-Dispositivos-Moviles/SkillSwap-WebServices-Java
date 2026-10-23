package com.innovify.skillswap.assessmentpeerreview.application.eventhandlers;

import com.innovify.skillswap.iam.application.acl.PushNotificationOutcome;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

/**
 * Sends the push notifications of Assessment &amp; Peer Review to a student (Firebase Cloud Messaging, through
 * Identity &amp; Access). They are written in Latin American Spanish, the language of the students: they are sent after
 * the request, so there is no Accept-Language to follow. When the student did not grant the notification permission
 * (no device token) nothing is sent; the result is still returned by the queries of the context.
 */
final class StudentPushNotifier {

    private static final Logger log = LoggerFactory.getLogger(StudentPushNotifier.class);

    static final Locale NOTIFICATION_LOCALE = Locale.forLanguageTag("es-419");

    private final UserNotificationsContextFacade notifications;
    private final MessageSource messageSource;

    StudentPushNotifier(UserNotificationsContextFacade notifications, MessageSource messageSource) {
        this.notifications = notifications;
        this.messageSource = messageSource;
    }

    /**
     * @param type          the kind of notification, so the app opens the right screen
     * @param data          the ids and values the app needs; the type is added to it
     * @param bodyArguments the arguments of the body message
     */
    void send(int studentId, String type, String titleCode, String bodyCode, Map<String, String> data,
              Object... bodyArguments) {
        Map<String, String> payload = new HashMap<>(data);
        payload.put("type", type);
        PushNotificationOutcome outcome = notifications.sendPushNotification(studentId, message(titleCode),
                message(bodyCode, bodyArguments), payload);
        log.info("Push notification {} to the user {}: {}", type, studentId, outcome);
    }

    /** The skill as a person reads it: the tag without its hyphens. */
    static String skillName(String skillTag) {
        return skillTag == null ? "" : skillTag.replace('-', ' ');
    }

    String message(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, code, NOTIFICATION_LOCALE);
    }
}
