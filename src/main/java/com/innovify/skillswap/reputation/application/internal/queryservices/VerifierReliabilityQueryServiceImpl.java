package com.innovify.skillswap.reputation.application.internal.queryservices;

import com.innovify.skillswap.reputation.application.queryservices.VerifierReliabilityQueryService;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierReliabilityByUserIdQuery;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import java.util.Optional;

public class VerifierReliabilityQueryServiceImpl implements VerifierReliabilityQueryService {

    private final VerifierReliabilityRepository repository;

    public VerifierReliabilityQueryServiceImpl(VerifierReliabilityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<VerifierReliability> handle(GetVerifierReliabilityByUserIdQuery query) {
        return repository.findByVerifierUserId(query.verifierUserId());
    }
}
