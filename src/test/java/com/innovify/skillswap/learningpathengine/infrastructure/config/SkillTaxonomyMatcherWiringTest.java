package com.innovify.skillswap.learningpathengine.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiClient;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiGoalInterpretationSettings;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiSettings;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.KeywordSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.SkillCatalog;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which interpretation of the goal is wired, depending on the settings. */
class SkillTaxonomyMatcherWiringTest {

    private static final SkillCatalog CATALOG = SkillCatalog.loadEmbedded();
    private static final GeminiSettings SETTINGS = new GeminiSettings("key", "model", List.of(), null, 30, 60, 1,
            0, "http://127.0.0.1:1/v1beta/");
    private static final GeminiClient CLIENT = new GeminiClient(SETTINGS, HttpClient.newHttpClient());

    @Test
    void withAKeyAndTheInterpretationEnabled_usesGeminiWithTheKeywordFallback() {
        var matcher = LearningPathEngineConfig.skillTaxonomyMatcherFor(CATALOG, SETTINGS,
                new GeminiGoalInterpretationSettings(true, 20), CLIENT);

        assertThat(matcher).isInstanceOf(GeminiSkillTaxonomyMatcher.class);
    }

    @Test
    void withTheInterpretationDisabled_usesOnlyTheKeywords() {
        var matcher = LearningPathEngineConfig.skillTaxonomyMatcherFor(CATALOG, SETTINGS,
                new GeminiGoalInterpretationSettings(false, 20), CLIENT);

        assertThat(matcher).isInstanceOf(KeywordSkillTaxonomyMatcher.class);
    }

    @Test
    void withoutGeminiSettings_usesOnlyTheKeywords() {
        var matcher = LearningPathEngineConfig.skillTaxonomyMatcherFor(CATALOG, null,
                new GeminiGoalInterpretationSettings(true, 20), CLIENT);

        assertThat(matcher).isInstanceOf(KeywordSkillTaxonomyMatcher.class);
    }

    @Test
    void theInterpretationTimeout_mustBePositive() {
        assertThatThrownBy(() -> new GeminiGoalInterpretationSettings(true, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
