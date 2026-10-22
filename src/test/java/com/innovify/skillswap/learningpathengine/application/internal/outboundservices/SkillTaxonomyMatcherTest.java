package com.innovify.skillswap.learningpathengine.application.internal.outboundservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.application.fakes.FakeSkillTaxonomyMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SkillTaxonomyMatcherTest {

    @Test
    void rank_ofAMatcherWithoutScores_keepsTheOrderOfMatchWithTheHighestAffinity() {
        var matcher = FakeSkillTaxonomyMatcher.sample();

        assertThat(matcher.rank("quiero rest y jwt"))
                .containsExactly(new SkillCandidate("authentication-jwt", 1.0),
                        new SkillCandidate("rest-api-design", 1.0));
    }

    @Test
    void rank_ofATextWithoutSkills_isEmpty() {
        assertThat(FakeSkillTaxonomyMatcher.sample().rank("quiero cocinar")).isEmpty();
    }

    @Test
    void candidate_keepsItsTagAndAffinity() {
        SkillCandidate candidate = new SkillCandidate("oop", 0.25);

        assertThat(candidate.tag()).isEqualTo("oop");
        assertThat(candidate.affinity()).isEqualTo(0.25);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
    void candidate_withAnAffinityOutOfRange_isRejected(double affinity) {
        assertThatThrownBy(() -> new SkillCandidate("oop", affinity)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void candidate_withoutATag_isRejected(String tag) {
        assertThatThrownBy(() -> new SkillCandidate(tag, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkillCandidate(null, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
