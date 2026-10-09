package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to the "learning_paths" table. Only {@link LearningPathRepositoryAdapter} uses it. */
public interface LearningPathJpaRepository extends JpaRepository<LearningPath, Integer> {

    Optional<LearningPath> findFirstByStudentIdOrderByIdDesc(int studentId);

    List<LearningPath> findByStudentIdOrderByIdDesc(int studentId);

    long countByStudentIdAndAdvancedFalse(int studentId);

    long countByStudentIdAndStatusAndAdvancedFalse(int studentId, PathStatus status);

    /**
     * Transaction-level advisory lock of PostgreSQL on (namespace, student): released at the commit or rollback.
     * Unlike SELECT ... FOR UPDATE on the paths, it also serializes a student who has no path yet.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:namespace, :studentId)) AS locked",
            nativeQuery = true)
    Integer lockStudent(@Param("namespace") int namespace, @Param("studentId") int studentId);

    @Query("select p from LearningPath p join p.nodes n where n.id = :nodeId")
    Optional<LearningPath> findByNodeId(@Param("nodeId") int nodeId);

    @Query("select distinct n.skillTag from LearningPath p join p.nodes n "
            + "where p.studentId = :studentId and n.status = :status")
    List<String> findSkillTagsByStudentIdAndNodeStatus(@Param("studentId") int studentId,
                                                       @Param("status") NodeStatus status);
}
