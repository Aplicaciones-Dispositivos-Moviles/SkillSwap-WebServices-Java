package com.innovify.skillswap.iam.application.acl;

import com.innovify.skillswap.iam.application.internal.outboundservices.PushDeliveryResult;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotification;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotificationSender;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserNotificationsContextFacadeImpl implements UserNotificationsContextFacade {

    private static final Logger log = LoggerFactory.getLogger(UserNotificationsContextFacadeImpl.class);

    private final UserRepository userRepository;
    private final PushNotificationSender pushNotificationSender;

    public UserNotificationsContextFacadeImpl(UserRepository userRepository,
                                              PushNotificationSender pushNotificationSender) {
        this.userRepository = userRepository;
        this.pushNotificationSender = pushNotificationSender;
    }

    @Override
    public PushNotificationOutcome sendPushNotification(int userId, String title, String body,
                                                        Map<String, String> data) {
        try {
            Optional<User> found = userRepository.findById(userId);
            if (found.isEmpty()) {
                return PushNotificationOutcome.USER_NOT_FOUND;
            }
            User user = found.get();
            if (!user.hasDeviceToken()) {
                return PushNotificationOutcome.NO_DEVICE_TOKEN;
            }

            PushDeliveryResult result = pushNotificationSender.send(user.getDeviceToken().value(),
                    new PushNotification(title, body, data));
            return switch (result) {
                case SENT -> PushNotificationOutcome.SENT;
                case FAILED -> PushNotificationOutcome.FAILED;
                case INVALID_TOKEN -> {
                    // The app was uninstalled or the token rotated: stop sending to it until a new one arrives.
                    log.info("The device token {} of the user {} is no longer valid and was removed",
                            user.getDeviceToken().abbreviated(), userId);
                    userRepository.save(user.removeDeviceToken());
                    yield PushNotificationOutcome.INVALID_DEVICE_TOKEN;
                }
            };
        } catch (RuntimeException exception) {
            log.error("The push notification for the user {} could not be sent", userId, exception);
            return PushNotificationOutcome.FAILED;
        }
    }
}
