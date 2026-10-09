package com.innovify.skillswap.iam.infrastructure.config;

import com.innovify.skillswap.iam.application.eventhandlers.SendVerificationEmailEventHandler;
import com.innovify.skillswap.iam.application.internal.commandservices.EmailVerificationIssuer;
import com.innovify.skillswap.iam.application.internal.emails.VerificationEmailComposer;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailSender;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotificationSender;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.iam.infrastructure.email.EmailSettings;
import com.innovify.skillswap.iam.infrastructure.email.brevo.BrevoEmailSenderAdapter;
import com.innovify.skillswap.iam.infrastructure.email.logging.LoggingEmailSenderAdapter;
import com.innovify.skillswap.iam.infrastructure.push.FirebaseSettings;
import com.innovify.skillswap.iam.infrastructure.push.firebase.FirebasePushNotificationAdapter;
import com.innovify.skillswap.iam.infrastructure.push.logging.LoggingPushNotificationAdapter;
import com.innovify.skillswap.iam.infrastructure.tokens.jwt.TokenSettings;
import com.innovify.skillswap.iam.infrastructure.verification.EmailVerificationSettings;
import com.innovify.skillswap.iam.domain.services.DefaultEmailDomainValidator;
import com.innovify.skillswap.iam.domain.services.EmailDomainValidator;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wiring of the IAM beans that are not annotated themselves. */
@Configuration
@EnableConfigurationProperties({TokenSettings.class, EmailSettings.class, EmailVerificationSettings.class,
        FirebaseSettings.class})
public class IamConfig {

    private static final Logger log = LoggerFactory.getLogger(IamConfig.class);

    @Bean
    public EmailDomainValidator emailDomainValidator() {
        return new DefaultEmailDomainValidator();
    }

    /**
     * Brevo when its API key and the sender address are configured; otherwise the emails are only logged, so the
     * application works without a Brevo account. Only which one is used is logged, never the key.
     */
    @Bean
    public EmailSender emailSender(EmailSettings settings) {
        if (!settings.hasBrevoApiKey()) {
            log.warn("BREVO_API_KEY is not set: emails are NOT sent, they are written to the log.");
            return new LoggingEmailSenderAdapter();
        }
        if (!settings.hasSenderAddress()) {
            log.error("BREVO_API_KEY is set but EMAIL_SENDER_ADDRESS is not: emails are NOT sent, they are written "
                    + "to the log.");
            return new LoggingEmailSenderAdapter();
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(settings.readTimeoutSeconds()));

        RestClient client = BrevoEmailSenderAdapter.configure(RestClient.builder().requestFactory(requestFactory),
                settings).build();
        log.info("Emails are sent through Brevo from {}.", settings.senderAddress());
        return new BrevoEmailSenderAdapter(client, settings.senderAddress(), settings.senderName());
    }

    @Bean
    public EmailVerificationIssuer emailVerificationIssuer(UserRepository userRepository,
                                                           DomainEventPublisher eventPublisher,
                                                           EmailVerificationSettings settings) {
        return new EmailVerificationIssuer(userRepository, eventPublisher, settings.tokenTtl(),
                settings.resendCooldown(), Clock.systemUTC());
    }

    @Bean
    public DomainEventHandler<EmailVerificationRequested> sendVerificationEmailEventHandler(
            EmailSender emailSender, EmailVerificationSettings settings) {
        return new SendVerificationEmailEventHandler(emailSender, new VerificationEmailComposer(settings.baseUrl()));
    }

    /**
     * Firebase Cloud Messaging when the service account is configured; otherwise the push notifications are only
     * logged, so the application works without a Firebase project. Invalid credentials do not stop the application
     * either: they are reported in the log and the notifications are logged.
     */
    @Bean
    public PushNotificationSender pushNotificationSender(FirebaseSettings settings) {
        if (!settings.hasCredentials()) {
            log.warn("FIREBASE_CREDENTIALS_BASE64 is not set: push notifications are NOT sent, they are written to "
                    + "the log.");
            return new LoggingPushNotificationAdapter();
        }
        try {
            FirebasePushNotificationAdapter adapter = FirebasePushNotificationAdapter.fromCredentials(
                    settings.credentialsBase64());
            log.info("Push notifications are sent through Firebase Cloud Messaging.");
            return adapter;
        } catch (RuntimeException exception) {
            log.error("Firebase could not be initialized ({}): push notifications are NOT sent, they are written to "
                    + "the log.", exception.getMessage());
            return new LoggingPushNotificationAdapter();
        }
    }
}
