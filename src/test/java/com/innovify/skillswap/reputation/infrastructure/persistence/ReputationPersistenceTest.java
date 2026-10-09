package com.innovify.skillswap.reputation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.StudentEmployabilityScoreRepository;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.services.DefaultEmployabilityScoreCalculator;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ReputationPersistenceTest extends PostgresIntegrationTest {

    private final DefaultVerifierReliabilityCalculator reliabilityCalculator = new DefaultVerifierReliabilityCalculator();
    private final DefaultEmployabilityScoreCalculator employabilityCalculator = new DefaultEmployabilityScoreCalculator();

    @Autowired
    private VerifierReliabilityRepository reliabilities;

    @Autowired
    private StudentEmployabilityScoreRepository employabilities;

    @Test
    void reliability_isStoredWithItsCountersAndScore() {
        VerifierReliability reliability = new VerifierReliability(7);
        reliability.recordResolution(reliabilityCalculator);
        reliability.recordResolution(reliabilityCalculator);
        reliability.recordOverturn(reliabilityCalculator);
        reliabilities.save(reliability);

        VerifierReliability loaded = reliabilities.findByVerifierUserId(7).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getResolvedCasesCount()).isEqualTo(2);
        assertThat(loaded.getOverturnedDecisionsCount()).isEqualTo(1);
        assertThat(loaded.getSanctionsCount()).isZero();
        assertThat(loaded.getScore().value()).isEqualTo(85);
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void reliability_isUpdatedInPlace() throws Exception {
        reliabilities.save(new VerifierReliability(7));

        VerifierReliability loaded = reliabilities.findByVerifierUserId(7).orElseThrow();
        loaded.applySanction(reliabilityCalculator);
        reliabilities.save(loaded);

        assertThat(queryString("SELECT count(*) FROM verifier_reliabilities")).isEqualTo("1");
        assertThat(reliabilities.findByVerifierUserId(7).orElseThrow().getScore().value()).isEqualTo(75);
    }

    @Test
    void reliability_findsNothingForAnUnknownVerifier() {
        assertThat(reliabilities.findByVerifierUserId(99)).isEmpty();
    }

    @Test
    void reliability_rejectsASecondRowForTheSameVerifier() {
        reliabilities.save(new VerifierReliability(7));

        assertThatThrownBy(() -> reliabilities.save(new VerifierReliability(7)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void employability_isStoredWithItsCountAndScore() {
        StudentEmployabilityScore score = new StudentEmployabilityScore(3);
        score.recordSkillVerified(employabilityCalculator);
        score.recordSkillVerified(employabilityCalculator);
        employabilities.save(score);

        StudentEmployabilityScore loaded = employabilities.findByStudentId(3).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getVerifiedSkillsCount()).isEqualTo(2);
        assertThat(loaded.getScore().value()).isEqualTo(20);
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void employability_findsNothingForAnUnknownStudent() {
        assertThat(employabilities.findByStudentId(99)).isEmpty();
    }

    @Test
    void employability_rejectsASecondRowForTheSameStudent() {
        employabilities.save(new StudentEmployabilityScore(3));

        assertThatThrownBy(() -> employabilities.save(new StudentEmployabilityScore(3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
