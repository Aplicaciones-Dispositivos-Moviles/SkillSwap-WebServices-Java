package com.innovify.skillswap.learningpathengine.domain.services;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.MiniProjectDraft;

/**
 * Generates the statement and the rubric of a mini-project for a skill, hiding which generative AI provider is used.
 * Any failure (provider down, unusable answer) is signaled with an unchecked exception.
 */
public interface MiniProjectGenerationService {

    MiniProjectDraft generateMiniProject(String skillTag);
}
