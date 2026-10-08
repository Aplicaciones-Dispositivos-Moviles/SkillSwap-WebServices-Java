package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link LearningPathRepository} port on top of Spring Data JPA. */
@Repository
public class LearningPathRepositoryAdapter implements LearningPathRepository {

    private final LearningPathJpaRepository jpaRepository;

    public LearningPathRepositoryAdapter(LearningPathJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public LearningPath save(LearningPath path) {
        return jpaRepository.saveAndFlush(path);
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
