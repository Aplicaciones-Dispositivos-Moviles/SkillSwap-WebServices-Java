package com.innovify.skillswap.credentialverification.infrastructure.config;

import com.innovify.skillswap.credentialverification.application.eventhandlers.NotifyCertificateResolutionEventHandler;
import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import com.innovify.skillswap.credentialverification.domain.model.events.CertificateVerificationResolved;
import com.innovify.skillswap.credentialverification.domain.services.CertificateRiskScorer;
import com.innovify.skillswap.credentialverification.domain.services.DefaultCertificateRiskScorer;
import com.innovify.skillswap.credentialverification.domain.services.HolderNameMatcher;
import com.innovify.skillswap.credentialverification.infrastructure.filestorage.CloudinarySettings;
import com.innovify.skillswap.credentialverification.infrastructure.filestorage.CloudinaryStorageService;
import com.innovify.skillswap.iam.application.acl.UserNotificationsContextFacade;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the Credential Verification beans that are not annotated themselves. */
@Configuration
@EnableConfigurationProperties(CloudinarySettings.class)
public class CredentialVerificationConfig {

    @Bean
    public CertificateRiskScorer certificateRiskScorer() {
        return new DefaultCertificateRiskScorer();
    }

    @Bean
    public HolderNameMatcher holderNameMatcher() {
        return new HolderNameMatcher();
    }

    @Bean
    public FileStorageService fileStorageService(CloudinarySettings settings) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new CloudinaryStorageService(settings, httpClient, Clock.systemUTC());
    }

    @Bean
    public DomainEventHandler<CertificateVerificationResolved> notifyCertificateResolutionEventHandler(
            UserNotificationsContextFacade notifications, MessageSource messageSource) {
        return new NotifyCertificateResolutionEventHandler(notifications, messageSource);
    }
}
