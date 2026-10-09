package com.innovify.skillswap.support;

import com.innovify.skillswap.iam.application.fakes.FakePushNotificationSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Records the push notifications instead of sending them through Firebase. */
@TestConfiguration
public class PushNotificationTestConfig {

    @Bean
    @Primary
    public FakePushNotificationSender testPushNotificationSender() {
        return new FakePushNotificationSender();
    }
}
