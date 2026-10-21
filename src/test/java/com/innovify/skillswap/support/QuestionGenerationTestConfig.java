package com.innovify.skillswap.support;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.domain.services.MiniProjectEvaluationService;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.MiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces Gemini with in-memory fakes (questions, mini-projects and their preliminary evaluation), so the integration
 * tests never use the network.
 */
@TestConfiguration
public class QuestionGenerationTestConfig {

    @Bean
    @Primary
    public QuestionGenerationService testQuestionGenerationService() {
        return new FakeQuestionGenerationService();
    }

    @Bean
    @Primary
    public MiniProjectGenerationService testMiniProjectGenerationService() {
        return new FakeMiniProjectGenerationService();
    }

    @Bean
    @Primary
    public MiniProjectEvaluationService testMiniProjectEvaluationService() {
        return new FakeMiniProjectEvaluationService();
    }
}
