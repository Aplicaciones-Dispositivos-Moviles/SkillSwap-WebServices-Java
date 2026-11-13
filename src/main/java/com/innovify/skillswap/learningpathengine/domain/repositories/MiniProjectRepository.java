package com.innovify.skillswap.learningpathengine.domain.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import java.util.Optional;

/** Persistence port of the {@link MiniProject} aggregate. */
public interface MiniProjectRepository {

    /** Persists a new mini-project and flushes right away. */
    MiniProject save(MiniProject miniProject);

    Optional<MiniProject> findById(int id);

    /** The most recently generated mini-project of a node. */
    Optional<MiniProject> findLatestByPathNodeId(int pathNodeId);
}
