package com.innovify.skillswap.subscriptionbilling.infrastructure.config;

import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.SubscriptionCommandService;
import com.innovify.skillswap.subscriptionbilling.application.internal.commandservices.SubscriptionCommandServiceImpl;
import com.innovify.skillswap.subscriptionbilling.application.internal.outboundservices.WebhookAuthorizationVerifier;
import com.innovify.skillswap.subscriptionbilling.application.internal.queryservices.SubscriptionQueryServiceImpl;
import com.innovify.skillswap.subscriptionbilling.application.queryservices.SubscriptionQueryService;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.ProcessedWebhookEventRepository;
import com.innovify.skillswap.subscriptionbilling.domain.repositories.SubscriptionRepository;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat.RevenueCatGatewayAdapter;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat.RevenueCatSettings;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat.RevenueCatWebhookAuthorization;
import com.innovify.skillswap.subscriptionbilling.infrastructure.payments.simulated.SimulatedPaymentGatewayAdapter;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClient;

/** Wiring of the Subscription &amp; Billing beans that are not annotated themselves. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({RevenueCatSettings.class, BillingSettings.class})
public class SubscriptionBillingConfig {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingConfig.class);

    /**
     * RevenueCat when its API key is configured; otherwise the simulated gateway, so the application works
     * without a RevenueCat account. Only which one is used is logged, never the key.
     */
    @Bean
    public PaymentGateway paymentGateway(RevenueCatSettings settings, BillingSettings billing) {
        if (!settings.hasApiKey()) {
            log.warn("REVENUECAT_API_KEY is not set: purchases are SIMULATED and nothing is charged.");
            return new SimulatedPaymentGatewayAdapter(billing.simulatedPeriod());
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(settings.readTimeoutSeconds()));

        RestClient client = RevenueCatGatewayAdapter.configure(RestClient.builder().requestFactory(requestFactory),
                settings).build();
        log.info("Purchases are verified with RevenueCat (entitlement '{}').", settings.entitlementId());
        return new RevenueCatGatewayAdapter(client, settings.entitlementId());
    }

    @Bean
    public WebhookAuthorizationVerifier webhookAuthorizationVerifier(RevenueCatSettings settings) {
        if (settings.webhookAuthorization() == null) {
            log.warn("REVENUECAT_WEBHOOK_AUTH is not set: every RevenueCat webhook notification is rejected.");
        }
        return new RevenueCatWebhookAuthorization(settings.webhookAuthorization());
    }

    @Bean
    public SubscriptionCommandService subscriptionCommandService(SubscriptionRepository subscriptions,
                                                                 ProcessedWebhookEventRepository processedEvents,
                                                                 PaymentGateway paymentGateway,
                                                                 DomainEventPublisher eventPublisher,
                                                                 TransactionOperations transactionOperations,
                                                                 RevenueCatSettings settings,
                                                                 MessageSource messageSource) {
        return new SubscriptionCommandServiceImpl(subscriptions, processedEvents, paymentGateway, eventPublisher,
                transactionOperations, settings.acceptSandboxEvents(), messageSource);
    }

    @Bean
    public SubscriptionQueryService subscriptionQueryService(SubscriptionRepository subscriptions) {
        return new SubscriptionQueryServiceImpl(subscriptions);
    }
}
