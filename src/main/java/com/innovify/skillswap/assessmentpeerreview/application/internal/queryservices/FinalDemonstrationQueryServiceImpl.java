package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.DemonstrationVideoLink;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FinalDemonstrationQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssignedFinalDemonstrationsQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetDemonstrationVideoQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationsByStudentIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import com.innovify.skillswap.credentialverification.application.acl.FileStorageContextFacade;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FinalDemonstrationQueryServiceImpl implements FinalDemonstrationQueryService {

    /** How long the link to a video works. */
    static final Duration VIDEO_LINK_VALIDITY = Duration.ofMinutes(15);

    private final FinalDemonstrationRepository demonstrationRepository;
    private final FileStorageContextFacade fileStorage;

    public FinalDemonstrationQueryServiceImpl(FinalDemonstrationRepository demonstrationRepository,
                                              FileStorageContextFacade fileStorage) {
        this.demonstrationRepository = demonstrationRepository;
        this.fileStorage = fileStorage;
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

    @Override
    public Optional<DemonstrationVideoLink> handle(GetDemonstrationVideoQuery query) {
        return demonstrationRepository.findById(query.demonstrationId())
                .filter(FinalDemonstration::hasVideo)
                .map(demonstration -> new DemonstrationVideoLink(
                        fileStorage.getTemporaryUrl(demonstration.getVideoStorageReference(), VIDEO_LINK_VALIDITY),
                        demonstration.getVideoContentType(), Instant.now().plus(VIDEO_LINK_VALIDITY)));
    }
}
