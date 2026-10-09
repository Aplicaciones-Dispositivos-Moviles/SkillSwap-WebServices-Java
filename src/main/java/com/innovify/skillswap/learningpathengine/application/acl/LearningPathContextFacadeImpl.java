package com.innovify.skillswap.learningpathengine.application.acl;

import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LearningPathContextFacadeImpl implements LearningPathContextFacade {

    private final LearningPathRepository learningPathRepository;
    private final AssessmentBlueprintRepository blueprintRepository;
    private final LearningPathCommandService learningPathCommandService;

    public LearningPathContextFacadeImpl(LearningPathRepository learningPathRepository,
                                         AssessmentBlueprintRepository blueprintRepository,
                                         LearningPathCommandService learningPathCommandService) {
        this.learningPathRepository = learningPathRepository;
        this.blueprintRepository = blueprintRepository;
        this.learningPathCommandService = learningPathCommandService;
    }

    @Override
    public Optional<BlueprintView> getBlueprint(int blueprintId) {
        Optional<AssessmentBlueprint> found = blueprintRepository.findById(blueprintId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AssessmentBlueprint blueprint = found.get();

        Optional<LearningPath> path = learningPathRepository.findByNodeId(blueprint.getPathNodeId());
        Optional<PathNode> node = path.flatMap(p -> p.getNode(blueprint.getPathNodeId()));
        if (path.isEmpty() || node.isEmpty()) {
            return Optional.empty();
        }

        List<BlueprintQuestionView> questions = blueprint.getQuestions().stream()
                .map(q -> new BlueprintQuestionView(q.getQuestionString(), q.getAnswers(), q.getCorrectAnswer()))
                .toList();

        return Optional.of(new BlueprintView(
                blueprint.getId(),
                blueprint.getPathNodeId(),
                path.get().getStudentId(),
                blueprint.getSkillTag(),
                Objects.equals(node.get().getAssessmentBlueprintId(), blueprint.getId()),
                // A paused path keeps its nodes, but no new attempt can be made on them until it is resumed.
                node.get().getStatus() == NodeStatus.AVAILABLE && path.get().isActive(),
                questions));
    }

    @Override
    public NodeCompletionOutcome completeNode(int pathNodeId) {
        Result<LearningPath> result = learningPathCommandService.handle(new CompletePathNodeCommand(pathNodeId));
        if (result.isSuccess()) {
            return NodeCompletionOutcome.COMPLETED;
        }

        if (result.error() instanceof LearningPathError error) {
            return switch (error) {
                case NODE_NOT_FOUND -> NodeCompletionOutcome.NODE_NOT_FOUND;
                case NODE_LOCKED -> NodeCompletionOutcome.NODE_LOCKED;
                case NODE_ALREADY_COMPLETED -> NodeCompletionOutcome.ALREADY_COMPLETED;
                default -> NodeCompletionOutcome.FAILED;
            };
        }
        return NodeCompletionOutcome.FAILED;
    }

    @Override
    public boolean hasCompletedSkill(int studentId, String skillTag) {
        return learningPathRepository.findCompletedSkillTagsByStudentId(studentId).contains(skillTag);
    }
}
