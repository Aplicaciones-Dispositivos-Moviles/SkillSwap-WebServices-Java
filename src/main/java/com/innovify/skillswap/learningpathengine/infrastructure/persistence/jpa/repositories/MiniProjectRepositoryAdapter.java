package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.repositories.MiniProjectRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link MiniProjectRepository} port on top of Spring Data JPA. */
@Repository
public class MiniProjectRepositoryAdapter implements MiniProjectRepository {

    private final MiniProjectJpaRepository jpaRepository;

    public MiniProjectRepositoryAdapter(MiniProjectJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public MiniProject save(MiniProject miniProject) {
        return jpaRepository.saveAndFlush(miniProject);
    }

    @Override
    public Optional<MiniProject> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<MiniProject> findLatestByPathNodeId(int pathNodeId) {
        return jpaRepository.findFirstByPathNodeIdOrderByIdDesc(pathNodeId);
    }
}
