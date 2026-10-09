package com.innovify.skillswap.learningpathengine.domain.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence port of the {@link LearningPath} aggregate. */
public interface LearningPathRepository {

    /** Persists a new or updated path (nodes included) and flushes right away. */
    LearningPath save(LearningPath path);

    Optional<LearningPath> findById(int id);

    /** Every path of a student, whatever its status, newest first. */
    List<LearningPath> findByStudentId(int studentId);

    /**
     * How many paths of the student count toward the total the plan allows: all of them, whatever their status,
     * except the advanced ones.
     */
    int countByStudentId(int studentId);

    /** How many active paths of the student count toward the plan: the advanced ones are left out. */
    int countActiveByStudentId(int studentId);

    /**
     * Serializes the changes to the number of paths of a student until the current transaction ends, so two
     * requests cannot both see room under the limit of the plan. It works even when the student has no path yet.
     * It must run inside a transaction.
     */
    void lockStudentPaths(int studentId);

    /** The most recently created path of a student, whatever its status. */
    Optional<LearningPath> findLatestByStudentId(int studentId);

    /** The path that contains the node. */
    Optional<LearningPath> findByNodeId(int nodeId);

    /** The skills the student completed in any of their paths. */
    Collection<String> findCompletedSkillTagsByStudentId(int studentId);
}
