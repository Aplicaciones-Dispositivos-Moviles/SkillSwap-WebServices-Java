package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.repositories.MiniProjectRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeMiniProjectRepository implements MiniProjectRepository {

    private final List<MiniProject> miniProjects = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    public List<MiniProject> miniProjects() {
        return miniProjects;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public MiniProject save(MiniProject miniProject) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (miniProject.getId() == null) {
            ReflectionTestUtils.setField(miniProject, "id", nextId++);
        }
        if (!miniProjects.contains(miniProject)) {
            miniProjects.add(miniProject);
        }
        return miniProject;
    }

    @Override
    public Optional<MiniProject> findById(int id) {
        return miniProjects.stream().filter(m -> Objects.equals(m.getId(), id)).findFirst();
    }

    @Override
    public Optional<MiniProject> findLatestByPathNodeId(int pathNodeId) {
        return miniProjects.stream().filter(m -> m.getPathNodeId() == pathNodeId)
                .max(Comparator.comparing(MiniProject::getId));
    }
}
