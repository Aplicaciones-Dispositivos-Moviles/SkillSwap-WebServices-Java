package com.innovify.skillswap.support;

import com.innovify.skillswap.subscriptionbilling.application.fakes.FakePaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the payment gateway with a fake each test programs, so the integration tests never use the network. */
@TestConfiguration
public class PaymentGatewayTestConfig {

    @Bean
    @Primary
    public PaymentGateway testPaymentGateway() {
        return new FakePaymentGateway();
    }
}
