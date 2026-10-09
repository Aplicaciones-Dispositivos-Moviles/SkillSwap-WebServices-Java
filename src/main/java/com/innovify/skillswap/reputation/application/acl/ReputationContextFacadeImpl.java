package com.innovify.skillswap.reputation.application.acl;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Facade implementation over the reliability repository. */
public class ReputationContextFacadeImpl implements ReputationContextFacade {

    private final VerifierReliabilityRepository reliabilityRepository;

    public ReputationContextFacadeImpl(VerifierReliabilityRepository reliabilityRepository) {
        this.reliabilityRepository = reliabilityRepository;
    }

    @Override
    public boolean isSeniorVerifier(int userId) {
        return reliabilityRepository.findByVerifierUserId(userId)
                .map(VerifierReliability::isSeniorVerifier)
                .orElse(false);
    }

    @Override
    public Set<Integer> findSeniorVerifiers(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return reliabilityRepository.findByVerifierUserIds(Set.copyOf(userIds)).stream()
                .filter(VerifierReliability::isSeniorVerifier)
                .map(VerifierReliability::getVerifierUserId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
