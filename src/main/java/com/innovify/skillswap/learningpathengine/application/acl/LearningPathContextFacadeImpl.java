package com.innovify.skillswap.learningpathengine.application.acl;

import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.ReopenPathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.learningpathengine.domain.repositories.MiniProjectRepository;
import com.innovify.skillswap.shared.application.Result;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LearningPathContextFacadeImpl implements LearningPathContextFacade {

    private final LearningPathRepository learningPathRepository;
    private final AssessmentBlueprintRepository blueprintRepository;
    private final MiniProjectRepository miniProjectRepository;
    private final LearningPathCommandService learningPathCommandService;

    public LearningPathContextFacadeImpl(LearningPathRepository learningPathRepository,
                                         AssessmentBlueprintRepository blueprintRepository,
                                         MiniProjectRepository miniProjectRepository,
                                         LearningPathCommandService learningPathCommandService) {
        this.learningPathRepository = learningPathRepository;
        this.blueprintRepository = blueprintRepository;
        this.miniProjectRepository = miniProjectRepository;
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
                .map(q -> new BlueprintQuestionView(q.getQuestionString(), q.getAnswers(), q.getCorrectAnswer(),
                        q.getSubTopic()))
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
    public Optional<MiniProjectView> getMiniProject(int miniProjectId) {
        Optional<MiniProject> found = miniProjectRepository.findById(miniProjectId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MiniProject miniProject = found.get();

        Optional<LearningPath> path = learningPathRepository.findByNodeId(miniProject.getPathNodeId());
        Optional<PathNode> node = path.flatMap(p -> p.getNode(miniProject.getPathNodeId()));
        if (path.isEmpty() || node.isEmpty()) {
            return Optional.empty();
        }

        boolean isLatest = miniProjectRepository.findLatestByPathNodeId(miniProject.getPathNodeId())
                .map(latest -> Objects.equals(latest.getId(), miniProject.getId()))
                .orElse(false);
        return Optional.of(new MiniProjectView(
                miniProject.getId(),
                miniProject.getPathNodeId(),
                path.get().getStudentId(),
                miniProject.getSkillTag(),
                miniProject.getDueAt(),
                isLatest,
                // A paused path keeps its nodes, but nothing new can be delivered until it is resumed.
                node.get().getStatus() == NodeStatus.AVAILABLE && path.get().isActive()));
    }

    @Override
    public Optional<MiniProjectRubricView> getMiniProjectRubric(int miniProjectId) {
        return miniProjectRepository.findById(miniProjectId)
                .map(miniProject -> new MiniProjectRubricView(
                        miniProject.getId(),
                        miniProject.getSkillTag(),
                        miniProject.getTitle(),
                        miniProject.getStatement(),
                        miniProject.getRubric().stream()
                                .map(criterion -> new RubricCriterionView(criterion.getName(),
                                        criterion.getDescription(), criterion.getWeight()))
                                .toList(),
                        miniProject.getPassingScore()));
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
    public NodeReopenOutcome reopenNode(int pathNodeId) {
        Result<LearningPath> result = learningPathCommandService.handle(new ReopenPathNodeCommand(pathNodeId));
        if (result.isSuccess()) {
            return NodeReopenOutcome.REOPENED;
        }

        if (result.error() instanceof LearningPathError error) {
            return switch (error) {
                case NODE_NOT_FOUND -> NodeReopenOutcome.NODE_NOT_FOUND;
                case NODE_NOT_COMPLETED -> NodeReopenOutcome.NOT_COMPLETED;
                case NODE_COMPLETED_BY_CERTIFICATE -> NodeReopenOutcome.COMPLETED_BY_CERTIFICATE;
                default -> NodeReopenOutcome.FAILED;
            };
        }
        return NodeReopenOutcome.FAILED;
    }

    @Override
    public Optional<PathCompletionView> getPathCompletion(int pathId) {
        return learningPathRepository.findById(pathId).map(path -> new PathCompletionView(path.getId(),
                path.getStudentId(), path.getStatus() == PathStatus.COMPLETED,
                List.copyOf(new LinkedHashSet<>(path.getNodes().stream().map(PathNode::getSkillTag).toList()))));
    }

    @Override
    public boolean hasCompletedSkill(int studentId, String skillTag) {
        return learningPathRepository.findCompletedSkillTagsByStudentId(studentId).contains(skillTag);
    }

    @Override
    public List<SkillDemandView> getMostRequestedSkills(Instant from, Instant to, int limit) {
        if (from == null || to == null || !from.isBefore(to) || limit < 1) {
            return List.of();
        }

        Map<String, Integer> counts = new HashMap<>();
        for (List<String> goalSkillTags : learningPathRepository.findGoalSkillTagsCreatedBetween(from, to)) {
            // A path counts once for each skill of its goal, however many times the tag repeats.
            for (String skillTag : new LinkedHashSet<>(goalSkillTags)) {
                counts.merge(skillTag, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new SkillDemandView(entry.getKey(), entry.getValue()))
                .toList();
    }
}
