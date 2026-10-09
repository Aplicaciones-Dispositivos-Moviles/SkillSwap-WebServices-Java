package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link LearningPathRepository} port on top of Spring Data JPA. */
@Repository
public class LearningPathRepositoryAdapter implements LearningPathRepository {

    /** First key of the advisory locks on the paths of a student, so they never collide with other locks. */
    static final int STUDENT_PATHS_LOCK_NAMESPACE = 1001;

    private final LearningPathJpaRepository jpaRepository;

    public LearningPathRepositoryAdapter(LearningPathJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public LearningPath save(LearningPath path) {
        return jpaRepository.saveAndFlush(path);
    }

    @Override
    public Optional<LearningPath> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<LearningPath> findByStudentId(int studentId) {
        return jpaRepository.findByStudentIdOrderByIdDesc(studentId);
    }

    @Override
    public int countByStudentId(int studentId) {
        return Math.toIntExact(jpaRepository.countByStudentIdAndAdvancedFalse(studentId));
    }

    @Override
    public int countActiveByStudentId(int studentId) {
        return Math.toIntExact(jpaRepository.countByStudentIdAndStatusAndAdvancedFalse(studentId, PathStatus.ACTIVE));
    }

    @Override
    public void lockStudentPaths(int studentId) {
        jpaRepository.lockStudent(STUDENT_PATHS_LOCK_NAMESPACE, studentId);
    }

    @Override
    public Optional<LearningPath> findLatestByStudentId(int studentId) {
        return jpaRepository.findFirstByStudentIdOrderByIdDesc(studentId);
    }

    @Override
    public Optional<LearningPath> findByNodeId(int nodeId) {
        return jpaRepository.findByNodeId(nodeId);
    }

    @Override
    public Collection<String> findCompletedSkillTagsByStudentId(int studentId) {
        return jpaRepository.findSkillTagsByStudentIdAndNodeStatus(studentId, NodeStatus.COMPLETED);
    }
}
