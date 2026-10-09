package com.innovify.skillswap.iam.application.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.fakes.FakePushNotificationSender;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushDeliveryResult;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserNotificationsContextFacadeImplTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final FakePushNotificationSender sender = new FakePushNotificationSender();
    private final UserNotificationsContextFacadeImpl facade = new UserNotificationsContextFacadeImpl(repository,
            sender);

    private User userWithToken() {
        return repository.save(TestData.newUser().registerDeviceToken("fcm-token-of-ana"));
    }

    @Test
    void sendPushNotification_sendsToTheRegisteredDevice() {
        User ana = userWithToken();

        PushNotificationOutcome outcome = facade.sendPushNotification(ana.getId(), "Título", "Cuerpo",
                Map.of("certificateId", "3"));

        assertThat(outcome).isEqualTo(PushNotificationOutcome.SENT);
        assertThat(sender.sent()).singleElement().satisfies(push -> {
            assertThat(push.deviceToken()).isEqualTo("fcm-token-of-ana");
            assertThat(push.notification().title()).isEqualTo("Título");
            assertThat(push.notification().body()).isEqualTo("Cuerpo");
            assertThat(push.notification().data()).containsEntry("certificateId", "3");
        });
    }

    @Test
    void sendPushNotification_withoutADeviceToken_sendsNothing() {
        User ana = repository.save(TestData.newUser());

        assertThat(facade.sendPushNotification(ana.getId(), "t", "b", Map.of()))
                .isEqualTo(PushNotificationOutcome.NO_DEVICE_TOKEN);
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void sendPushNotification_toAnUnknownUser_sendsNothing() {
        assertThat(facade.sendPushNotification(99, "t", "b", Map.of()))
                .isEqualTo(PushNotificationOutcome.USER_NOT_FOUND);
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void sendPushNotification_whenTheTokenIsNoLongerValid_forgetsIt() {
        User ana = userWithToken();
        sender.answer(PushDeliveryResult.INVALID_TOKEN);

        assertThat(facade.sendPushNotification(ana.getId(), "t", "b", Map.of()))
                .isEqualTo(PushNotificationOutcome.INVALID_DEVICE_TOKEN);
        assertThat(ana.getDeviceToken()).isNull();
        assertThat(repository.saveCalls()).isEqualTo(2);
    }

    @Test
    void sendPushNotification_whenTheProviderFails_keepsTheToken() {
        User ana = userWithToken();
        sender.answer(PushDeliveryResult.FAILED);

        assertThat(facade.sendPushNotification(ana.getId(), "t", "b", Map.of()))
                .isEqualTo(PushNotificationOutcome.FAILED);
        assertThat(ana.getDeviceToken()).isNotNull();
    }

    @Test
    void sendPushNotification_neverThrows() {
        User ana = userWithToken();
        sender.answer(PushDeliveryResult.INVALID_TOKEN);
        repository.failOnSave(new IllegalStateException("database down"));

        assertThat(facade.sendPushNotification(ana.getId(), "t", "b", null))
                .isEqualTo(PushNotificationOutcome.FAILED);
    }
}
