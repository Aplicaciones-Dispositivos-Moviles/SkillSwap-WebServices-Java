package com.innovify.skillswap.reputation.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.reputation.application.fakes.FakeStudentEmployabilityScoreRepository;
import com.innovify.skillswap.reputation.application.fakes.FakeVerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.queries.GetStudentEmployabilityByStudentIdQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierReliabilityByUserIdQuery;
import org.junit.jupiter.api.Test;

class ReputationQueryServicesImplTest {

    @Test
    void reliabilityQuery_returnsTheReliabilityOfTheVerifierOrEmpty() {
        var repository = new FakeVerifierReliabilityRepository();
        VerifierReliability reliability = repository.save(new VerifierReliability(2));
        var service = new VerifierReliabilityQueryServiceImpl(repository);

        assertThat(service.handle(new GetVerifierReliabilityByUserIdQuery(2))).containsSame(reliability);
        assertThat(service.handle(new GetVerifierReliabilityByUserIdQuery(3))).isEmpty();
    }

    @Test
    void employabilityQuery_returnsTheScoreOfTheStudentOrEmpty() {
        var repository = new FakeStudentEmployabilityScoreRepository();
        StudentEmployabilityScore score = repository.save(new StudentEmployabilityScore(1));
        var service = new StudentEmployabilityQueryServiceImpl(repository);

        assertThat(service.handle(new GetStudentEmployabilityByStudentIdQuery(1))).containsSame(score);
        assertThat(service.handle(new GetStudentEmployabilityByStudentIdQuery(2))).isEmpty();
    }
}
