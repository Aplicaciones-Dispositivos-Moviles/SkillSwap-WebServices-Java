package com.innovify.skillswap.support;

import com.innovify.skillswap.iam.application.fakes.FakeEmailSender;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Records the emails instead of sending them, so a test can read the verification link. */
@TestConfiguration
public class EmailSenderTestConfig {

    @Bean
    @Primary
    public FakeEmailSender testEmailSender() {
        return new FakeEmailSender();
    }
}
