package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerifierProfileQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerifierProfileByUserIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class VerifierProfileQueryServiceImpl implements VerifierProfileQueryService {

    private final VerifierProfileRepository profileRepository;

    public VerifierProfileQueryServiceImpl(VerifierProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public Optional<VerifierProfile> handle(GetVerifierProfileByUserIdQuery query) {
        return profileRepository.findByUserId(query.userId());
    }
}
