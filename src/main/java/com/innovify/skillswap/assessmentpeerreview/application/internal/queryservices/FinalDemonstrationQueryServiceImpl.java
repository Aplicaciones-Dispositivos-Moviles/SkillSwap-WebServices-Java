package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FinalDemonstrationQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssignedFinalDemonstrationsQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationsByStudentIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FinalDemonstrationQueryServiceImpl implements FinalDemonstrationQueryService {

    private final FinalDemonstrationRepository demonstrationRepository;

    public FinalDemonstrationQueryServiceImpl(FinalDemonstrationRepository demonstrationRepository) {
        this.demonstrationRepository = demonstrationRepository;
    }

    @Override
    public Optional<FinalDemonstration> handle(GetFinalDemonstrationByIdQuery query) {
        return demonstrationRepository.findById(query.demonstrationId());
    }

    @Override
    public List<FinalDemonstration> handle(GetFinalDemonstrationsByStudentIdQuery query) {
        return demonstrationRepository.findByStudentId(query.studentId());
    }

    @Override
    public List<FinalDemonstration> handle(GetAssignedFinalDemonstrationsQuery query) {
        return demonstrationRepository.findByVerifierUserId(query.verifierUserId());
    }
}
