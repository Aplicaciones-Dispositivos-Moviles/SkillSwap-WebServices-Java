package com.innovify.skillswap.reputation.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.services.DefaultEmployabilityScoreCalculator;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.reputation.interfaces.rest.transform.ReputationResourceAssemblers;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ReputationResourceAssemblersTest {

    private static void setId(Object aggregate, int id) throws Exception {
        Field field = aggregate.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(aggregate, id);
    }

    @Test
    void employability_isConvertedWithItsCountAndScore() throws Exception {
        StudentEmployabilityScore score = new StudentEmployabilityScore(3);
        score.recordSkillVerified(new DefaultEmployabilityScoreCalculator());
        score.recordSkillVerified(new DefaultEmployabilityScoreCalculator());
        setId(score, 5);

        var resource = ReputationResourceAssemblers.toResource(score);

        assertThat(resource.id()).isEqualTo(5);
        assertThat(resource.studentId()).isEqualTo(3);
        assertThat(resource.verifiedSkillsCount()).isEqualTo(2);
        assertThat(resource.score()).isEqualTo(20);
        assertThat(resource.updatedAt()).isEqualTo(score.getUpdatedAt());
    }

    @Test
    void reliability_isConvertedWithItsCountersAndScore() throws Exception {
        var calculator = new DefaultVerifierReliabilityCalculator();
        VerifierReliability reliability = new VerifierReliability(7);
        reliability.recordResolution(calculator);
        reliability.recordOverturn(calculator);
        reliability.applySanction(calculator);
        setId(reliability, 9);

        var resource = ReputationResourceAssemblers.toResource(reliability);

        assertThat(resource.id()).isEqualTo(9);
        assertThat(resource.verifierUserId()).isEqualTo(7);
        assertThat(resource.resolvedCasesCount()).isEqualTo(1);
        assertThat(resource.overturnedDecisionsCount()).isEqualTo(1);
        assertThat(resource.sanctionsCount()).isEqualTo(1);
        assertThat(resource.score()).isEqualTo(60);
        assertThat(resource.updatedAt()).isEqualTo(reliability.getUpdatedAt());
    }
}
