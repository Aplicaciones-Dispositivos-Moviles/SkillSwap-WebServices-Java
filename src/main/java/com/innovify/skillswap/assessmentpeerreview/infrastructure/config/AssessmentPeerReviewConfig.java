package com.innovify.skillswap.assessmentpeerreview.infrastructure.config;

import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the Assessment &amp; Peer Review beans that are not annotated themselves. */
@Configuration
public class AssessmentPeerReviewConfig {

    @Bean
    public VerifierMatcher verifierMatcher() {
        return new DefaultVerifierMatcher();
    }
}
