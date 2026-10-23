package com.innovify.skillswap.assessmentpeerreview.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.MiniProjectBrief;
import com.innovify.skillswap.assessmentpeerreview.domain.services.MiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.ai.GeminiMiniProjectEvaluator;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiniProjectEvaluationWiringTest {

    private final AssessmentPeerReviewConfig config = new AssessmentPeerReviewConfig();

    @Test
    void enabled_evaluatesWithGemini() {
        MiniProjectEvaluationService service = config.miniProjectEvaluationService(null,
                new MiniProjectEvaluationSettings(true, 60, true), "Spanish");

        assertThat(service).isInstanceOf(GeminiMiniProjectEvaluator.class);
    }

    @Test
    void disabled_neverEvaluates_soEveryDeliveryGoesToAVerifier() {
        MiniProjectEvaluationService service = config.miniProjectEvaluationService(null,
                new MiniProjectEvaluationSettings(false, 60, true), "Spanish");
        MiniProjectBrief brief = new MiniProjectBrief("http-basics", "T", "S",
                List.of(new MiniProjectBrief.Criterion("Functionality", "It works.", 100)), 70);

        assertThatThrownBy(() -> service.evaluate(brief, "https://github.com/ana/repo"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("disabled");
    }

    @Test
    void settings_withoutAPositiveTimeout_areRejected() {
        assertThatThrownBy(() -> new MiniProjectEvaluationSettings(true, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
