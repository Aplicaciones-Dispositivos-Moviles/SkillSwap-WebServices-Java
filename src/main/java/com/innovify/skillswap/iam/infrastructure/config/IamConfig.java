package com.innovify.skillswap.iam.infrastructure.config;

import com.innovify.skillswap.iam.domain.services.DefaultEmailDomainValidator;
import com.innovify.skillswap.iam.domain.services.EmailDomainValidator;
import com.innovify.skillswap.iam.infrastructure.seeding.CoordinatorSeedSettings;
import com.innovify.skillswap.iam.infrastructure.tokens.jwt.TokenSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the IAM beans that are not annotated themselves. */
@Configuration
@EnableConfigurationProperties({TokenSettings.class, CoordinatorSeedSettings.class})
public class IamConfig {

    @Bean
    public EmailDomainValidator emailDomainValidator() {
        return new DefaultEmailDomainValidator();
    }
}
