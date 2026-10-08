package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeAssessmentBlueprintRepository implements AssessmentBlueprintRepository {

    private final List<AssessmentBlueprint> blueprints = new ArrayList<>();
    private int nextId = 1;
    private RuntimeException saveFailure;

    public List<AssessmentBlueprint> blueprints() {
        return blueprints;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public AssessmentBlueprint save(AssessmentBlueprint blueprint) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (blueprint.getId() == null) {
            ReflectionTestUtils.setField(blueprint, "id", nextId++);
        }
        if (!blueprints.contains(blueprint)) {
            blueprints.add(blueprint);
        }
        return blueprint;
    }

    @Override
    public Optional<AssessmentBlueprint> findById(int id) {
        return blueprints.stream().filter(b -> Objects.equals(b.getId(), id)).findFirst();
    }

    @Override
    public Optional<AssessmentBlueprint> findLatestByPathNodeId(int pathNodeId) {
        return blueprints.stream().filter(b -> b.getPathNodeId() == pathNodeId)
                .max(Comparator.comparing(AssessmentBlueprint::getId));
    }
}
