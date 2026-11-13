package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.AssessmentBlueprintResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.QuestionResource;
import java.util.function.Function;

public final class AssessmentBlueprintResourceFromEntityAssembler {

    private AssessmentBlueprintResourceFromEntityAssembler() {
    }

    /**
     * The correct answer of each question is omitted on purpose. It stays on the server so Assessment and Peer
     * Review can grade the attempt.
     */
    public static AssessmentBlueprintResource toResourceFromEntity(AssessmentBlueprint entity,
                                                                   Function<String, String> skillNameOf) {
        return new AssessmentBlueprintResource(
                entity.getId(),
                entity.getPathNodeId(),
                entity.getSkillTag(),
                skillNameOf.apply(entity.getSkillTag()),
                entity.getQuestions().stream()
                        .map(question -> new QuestionResource(question.getQuestionString(), question.getAnswers()))
                        .toList(),
                entity.getGeneratedAt());
    }
}
