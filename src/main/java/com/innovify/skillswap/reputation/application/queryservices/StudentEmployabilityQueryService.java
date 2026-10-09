package com.innovify.skillswap.reputation.application.queryservices;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.queries.GetStudentEmployabilityByStudentIdQuery;
import java.util.Optional;

/** Student employability query service interface. */
public interface StudentEmployabilityQueryService {

    Optional<StudentEmployabilityScore> handle(GetStudentEmployabilityByStudentIdQuery query);
}
