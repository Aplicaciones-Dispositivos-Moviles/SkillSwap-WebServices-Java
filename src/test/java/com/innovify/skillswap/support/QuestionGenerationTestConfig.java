package com.innovify.skillswap.support;

import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces Gemini with an in-memory fake, so the integration tests never use the network. */
@TestConfiguration
public class QuestionGenerationTestConfig {

    @Bean
    @Primary
    public QuestionGenerationService testQuestionGenerationService() {
        return new FakeQuestionGenerationService();
    }
}
