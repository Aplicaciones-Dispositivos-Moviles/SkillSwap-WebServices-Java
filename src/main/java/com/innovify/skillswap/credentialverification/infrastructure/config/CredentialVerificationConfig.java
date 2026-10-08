package com.innovify.skillswap.credentialverification.infrastructure.config;

import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import com.innovify.skillswap.credentialverification.domain.services.CertificateRiskScorer;
import com.innovify.skillswap.credentialverification.domain.services.DefaultCertificateRiskScorer;
import com.innovify.skillswap.credentialverification.infrastructure.filestorage.CloudinarySettings;
import com.innovify.skillswap.credentialverification.infrastructure.filestorage.CloudinaryStorageService;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    public FileStorageService fileStorageService(CloudinarySettings settings) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new CloudinaryStorageService(settings, httpClient, Clock.systemUTC());
    }
}
