package com.innovify.skillswap.learningpathengine.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "mini_projects" table. Only {@link MiniProjectRepositoryAdapter} uses it. */
public interface MiniProjectJpaRepository extends JpaRepository<MiniProject, Integer> {

    Optional<MiniProject> findFirstByPathNodeIdOrderByIdDesc(int pathNodeId);
}
