package com.innovify.skillswap.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.services.DefaultEmployabilityScoreCalculator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StudentEmployabilityScoreTest {

    private static final DefaultEmployabilityScoreCalculator CALCULATOR = new DefaultEmployabilityScoreCalculator();

    @Test
    void constructor_startsWithNoSkillsAndAZeroScore() {
        Instant before = Instant.now();

        StudentEmployabilityScore score = new StudentEmployabilityScore(5);

        assertThat(score.getStudentId()).isEqualTo(5);
        assertThat(score.getVerifiedSkillsCount()).isZero();
        assertThat(score.getScore().value()).isZero();
        assertThat(score.getUpdatedAt()).isBetween(before, Instant.now());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_withAnInvalidStudent_throwsDomainException(int studentId) {
        assertThatThrownBy(() -> new StudentEmployabilityScore(studentId)).isInstanceOf(DomainException.class);
    }

    @Test
    void recordSkillVerified_countsTheSkillAndRecalculatesTheScore() {
        StudentEmployabilityScore score = new StudentEmployabilityScore(5)
                .recordSkillVerified(CALCULATOR).recordSkillVerified(CALCULATOR);

        assertThat(score.getVerifiedSkillsCount()).isEqualTo(2);
        assertThat(score.getScore().value()).isEqualTo(20);
    }

    @Test
    void recordSkillVerified_stopsGrowingAtTheMaximum() {
        StudentEmployabilityScore score = new StudentEmployabilityScore(5);

        for (int i = 0; i < 12; i++) {
            score.recordSkillVerified(CALCULATOR);
        }

        assertThat(score.getVerifiedSkillsCount()).isEqualTo(12);
        assertThat(score.getScore().value()).isEqualTo(100);
    }

    @Test
    void recordSkillVerified_refreshesTheUpdatedAt() {
        StudentEmployabilityScore score = new StudentEmployabilityScore(5);
        Instant before = Instant.now();

        score.recordSkillVerified(CALCULATOR);

        assertThat(score.getUpdatedAt()).isBetween(before, Instant.now());
    }

    @Test
    void recordSkillVerified_withoutACalculator_throwsNullPointerException() {
        assertThatThrownBy(() -> new StudentEmployabilityScore(5).recordSkillVerified(null))
                .isInstanceOf(NullPointerException.class);
    }
}
