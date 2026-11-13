package com.innovify.skillswap.learningpathengine.application.queryservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetMiniProjectByPathNodeIdQuery;
import java.util.Optional;

/** Mini-project query service interface. */
public interface MiniProjectQueryService {

    /**
     * The latest mini-project generated for the node, only when the node belongs to the student: another student's
     * node and a node without a mini-project both answer empty.
     */
    Optional<MiniProject> handle(GetMiniProjectByPathNodeIdQuery query);
}
