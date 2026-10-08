package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.LearningPathResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.PathNodeResource;
import java.util.List;
import java.util.function.Function;

public final class LearningPathResourceFromEntityAssembler {

    private LearningPathResourceFromEntityAssembler() {
    }

    public static LearningPathResource toResourceFromEntity(LearningPath entity,
                                                            Function<String, String> skillNameOf) {
        List<PathNodeResource> nodes = entity.getNodes().stream()
                .map(node -> new PathNodeResource(
                        node.getId(),
                        node.getSkillTag(),
                        skillNameOf.apply(node.getSkillTag()),
                        node.getOrder(),
                        node.getStatus().value(),
                        node.getPrerequisiteSkillTags(),
                        node.getLinkedCertificateId(),
                        node.getAssessmentBlueprintId()))
                .toList();

        return new LearningPathResource(
                entity.getId(),
                entity.getStudentId(),
                entity.getCareerGoal().rawText(),
                entity.getCareerGoal().mappedSkillTags(),
                entity.getStatus().value(),
                nodes,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
