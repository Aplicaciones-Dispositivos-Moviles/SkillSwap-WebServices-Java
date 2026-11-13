package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetMiniProjectSubmissionDetailQuery;
import java.util.Optional;

/** Mini-project submission query service interface. */
public interface MiniProjectSubmissionQueryService {

    /** The delivery with its preliminary evaluation and its case, or empty when it does not exist. */
    Optional<MiniProjectSubmissionDetail> handle(GetMiniProjectSubmissionDetailQuery query);
}
