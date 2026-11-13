package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssignedFinalDemonstrationsQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetDemonstrationVideoQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationsByStudentIdQuery;
import java.util.List;
import java.util.Optional;

/** Final demonstration query service interface. */
public interface FinalDemonstrationQueryService {

    Optional<FinalDemonstration> handle(GetFinalDemonstrationByIdQuery query);

    /** The demonstrations of the student, the most recent first. */
    List<FinalDemonstration> handle(GetFinalDemonstrationsByStudentIdQuery query);

    /** The demonstrations assigned to the verifier that are not cancelled, the soonest first. */
    List<FinalDemonstration> handle(GetAssignedFinalDemonstrationsQuery query);

    /** A temporary link to the video of the demonstration, or empty when it does not exist or has no video yet. */
    Optional<DemonstrationVideoLink> handle(GetDemonstrationVideoQuery query);
}
