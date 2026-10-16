package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.MiniProjectResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.RubricCriterionResource;
import java.util.function.Function;

public final class MiniProjectResourceFromEntityAssembler {

    private MiniProjectResourceFromEntityAssembler() {
    }

    public static MiniProjectResource toResourceFromEntity(MiniProject entity, Function<String, String> skillNameOf) {
        return new MiniProjectResource(
                entity.getId(),
                entity.getPathNodeId(),
                entity.getSkillTag(),
                skillNameOf.apply(entity.getSkillTag()),
                entity.getTitle(),
                entity.getStatement(),
                entity.getRubric().stream()
                        .map(c -> new RubricCriterionResource(c.getName(), c.getDescription(), c.getWeight()))
                        .toList(),
                entity.getPassingScore(),
                entity.getCreatedAt(),
                entity.getDueAt());
    }
}
