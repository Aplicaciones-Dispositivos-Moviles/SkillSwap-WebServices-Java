package com.innovify.skillswap.learningpathengine.application.queryservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetLearningPathByStudentIdQuery;
import java.util.Optional;

/** Learning path query service interface. */
public interface LearningPathQueryService {

    /** The latest path of the student, whatever its status. */
    Optional<LearningPath> handle(GetLearningPathByStudentIdQuery query);
}
