package com.innovify.skillswap.reputation.application.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.reputation.application.fakes.FakeVerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReputationContextFacadeImplTest {

    private final FakeVerifierReliabilityRepository reliabilities = new FakeVerifierReliabilityRepository();
    private final ReputationContextFacadeImpl facade = new ReputationContextFacadeImpl(reliabilities);

    private void store(int userId, int resolved, int overturned) {
        var calculator = new DefaultVerifierReliabilityCalculator();
        VerifierReliability reliability = new VerifierReliability(userId);
        for (int i = 0; i < resolved; i++) {
            reliability.recordResolution(calculator);
        }
        for (int i = 0; i < overturned; i++) {
            reliability.recordOverturn(calculator);
        }
        reliabilities.save(reliability);
    }

    @Test
    void seniors_areTheGoldVerifiersWithAReliabilityOf90() {
        store(2, 100, 0);
        store(3, 99, 0);
        store(4, 120, 1);
        store(5, 150, 0);

        assertThat(facade.isSeniorVerifier(2)).isTrue();
        assertThat(facade.isSeniorVerifier(3)).isFalse();
        assertThat(facade.isSeniorVerifier(4)).isFalse();
        assertThat(facade.isSeniorVerifier(9)).isFalse();
        assertThat(facade.findSeniorVerifiers(List.of(2, 3, 4, 9))).containsExactly(2);
        assertThat(facade.findSeniorVerifiers(List.of())).isEmpty();
    }
}
