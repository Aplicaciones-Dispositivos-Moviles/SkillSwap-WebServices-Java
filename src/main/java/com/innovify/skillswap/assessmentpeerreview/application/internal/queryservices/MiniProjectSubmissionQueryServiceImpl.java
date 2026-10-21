package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.MiniProjectSubmissionDetail;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.MiniProjectSubmissionQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetMiniProjectSubmissionDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MiniProjectSubmissionQueryServiceImpl implements MiniProjectSubmissionQueryService {

    private final MiniProjectSubmissionRepository submissionRepository;
    private final VerificationCaseRepository caseRepository;

    public MiniProjectSubmissionQueryServiceImpl(MiniProjectSubmissionRepository submissionRepository,
                                                 VerificationCaseRepository caseRepository) {
        this.submissionRepository = submissionRepository;
        this.caseRepository = caseRepository;
    }

    @Override
    public Optional<MiniProjectSubmissionDetail> handle(GetMiniProjectSubmissionDetailQuery query) {
        return submissionRepository.findById(query.submissionId())
                .map(submission -> new MiniProjectSubmissionDetail(submission,
                        caseRepository.findBySubmissionId(submission.getId()).orElse(null)));
    }
}
