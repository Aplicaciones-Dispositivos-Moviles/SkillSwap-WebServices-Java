package com.innovify.skillswap.learningpathengine.domain.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import java.util.Collection;
import java.util.Optional;

/** Persistence port of the {@link LearningPath} aggregate. */
public interface LearningPathRepository {

    /** Persists a new or updated path (nodes included) and flushes right away. */
    LearningPath save(LearningPath path);

    /** The most recently created path of a student, whatever its status. */
    Optional<LearningPath> findLatestByStudentId(int studentId);

    /** The path that contains the node. */
    Optional<LearningPath> findByNodeId(int nodeId);

    /** The skills the student completed in any of their paths. */
    Collection<String> findCompletedSkillTagsByStudentId(int studentId);
}
