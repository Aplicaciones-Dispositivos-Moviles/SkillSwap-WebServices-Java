package com.innovify.skillswap.assessmentpeerreview.infrastructure.config;

import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.domain.services.MiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.domain.services.VerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.ai.GeminiMiniProjectEvaluator;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the Assessment &amp; Peer Review beans that are not annotated themselves. */
@Configuration
@EnableConfigurationProperties({ReviewDeadlineSettings.class, MiniProjectEvaluationSettings.class})
public class AssessmentPeerReviewConfig {

    @Bean
    public VerifierMatcher verifierMatcher() {
        return new DefaultVerifierMatcher();
    }

    /**
     * The preliminary evaluation of the mini-projects with Gemini (the same client, key and models as the question
     * generator). With mini-project-evaluation.enabled=false nothing is sent to Gemini and every delivery goes to a
     * verifier, as when Gemini fails.
     */
    @Bean
    public MiniProjectEvaluationService miniProjectEvaluationService(GeminiClient geminiClient,
                                                                     MiniProjectEvaluationSettings settings) {
        if (!settings.enabled()) {
            return (brief, repositoryUrl) -> {
                throw new IllegalStateException(
                        "The preliminary evaluation is disabled (mini-project-evaluation.enabled=false).");
            };
        }
        return new GeminiMiniProjectEvaluator(geminiClient, Duration.ofSeconds(settings.timeoutSeconds()));
    }
}
