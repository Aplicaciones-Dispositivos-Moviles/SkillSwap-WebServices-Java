package com.innovify.skillswap.learningpathengine.domain.services;

import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import java.util.List;

/**
 * Generates the questions of an assessment for a skill, hiding which generative AI provider is used. Any
 * failure (provider down, unusable answer) is signaled with an unchecked exception.
 */
public interface QuestionGenerationService {

    List<Question> generateQuestions(String skillTag);
}
