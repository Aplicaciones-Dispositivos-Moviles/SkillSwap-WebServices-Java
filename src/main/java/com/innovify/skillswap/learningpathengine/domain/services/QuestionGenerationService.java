package com.innovify.skillswap.learningpathengine.domain.services;

import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import java.util.Collection;
import java.util.List;

/**
 * Generates the questions of an assessment for a skill, hiding which generative AI provider is used. Any
 * failure (provider down, unusable answer) is signaled with an unchecked exception.
 */
public interface QuestionGenerationService {

    /** Questions for a first attempt: nothing to avoid. */
    default List<Question> generateQuestions(String skillTag) {
        return generateQuestions(skillTag, List.of());
    }

    /**
     * Questions for a new attempt, asking the provider not to repeat the questions of the previous attempts.
     * The provider may still repeat one: the caller checks it.
     *
     * @param excludedQuestions texts of the questions already asked for this node (may be empty)
     */
    List<Question> generateQuestions(String skillTag, Collection<String> excludedQuestions);
}
