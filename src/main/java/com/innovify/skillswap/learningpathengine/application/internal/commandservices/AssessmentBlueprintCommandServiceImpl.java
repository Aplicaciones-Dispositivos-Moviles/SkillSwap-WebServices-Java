package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import com.innovify.skillswap.learningpathengine.application.commandservices.AssessmentBlueprintCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateAssessmentBlueprintCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Assessment blueprint command service.
 *
 * <p>Like the other command services it is not {@code @Transactional}. The only transactional step is the
 * final one, which must save the blueprint and point the node to it as a unit; it runs in the given
 * {@link TransactionOperations}. Generating the questions (a slow call to an external provider) stays outside
 * of any transaction.
 */
@Service
public class AssessmentBlueprintCommandServiceImpl implements AssessmentBlueprintCommandService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentBlueprintCommandServiceImpl.class);

    private final LearningPathRepository learningPathRepository;
    private final AssessmentBlueprintRepository blueprintRepository;
    private final QuestionGenerationService questionGenerationService;
    private final TransactionOperations transactions;
    private final LearningPathFailures failures;

    public AssessmentBlueprintCommandServiceImpl(LearningPathRepository learningPathRepository,
                                                 AssessmentBlueprintRepository blueprintRepository,
                                                 QuestionGenerationService questionGenerationService,
                                                 TransactionOperations transactions,
                                                 MessageSource messageSource) {
        this.learningPathRepository = learningPathRepository;
        this.blueprintRepository = blueprintRepository;
        this.questionGenerationService = questionGenerationService;
        this.transactions = transactions;
        this.failures = new LearningPathFailures(messageSource);
    }

    @Override
    public Result<AssessmentBlueprint> handle(GenerateAssessmentBlueprintCommand command) {
        try {
            Optional<LearningPath> found = learningPathRepository.findByNodeId(command.pathNodeId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.NODE_NOT_FOUND);
            }
            LearningPath path = found.get();

            // Only the owner of the path can request (and therefore see) the questions of a node.
            if (path.getStudentId() != command.studentId()) {
                return failures.failure(LearningPathError.NOT_PATH_OWNER);
            }

            PathNode node = path.getNode(command.pathNodeId()).orElseThrow();
            if (node.getStatus() == NodeStatus.COMPLETED) {
                return failures.failure(LearningPathError.NODE_ALREADY_COMPLETED);
            }
            if (node.getStatus() == NodeStatus.LOCKED) {
                return failures.failure(LearningPathError.NODE_LOCKED,
                        Map.of("pendingPrerequisites", path.pendingPrerequisitesOf(node.getId())));
            }

            AssessmentBlueprint blueprint;
            try {
                List<Question> questions = questionGenerationService.generateQuestions(node.getSkillTag());
                blueprint = new AssessmentBlueprint(node.getId(), node.getSkillTag(), questions);
            } catch (RuntimeException exception) {
                // Provider failure, or output that does not meet the contract (a DomainException).
                log.error("Could not generate the assessment of node {} ({})", node.getId(), node.getSkillTag(),
                        exception);
                return failures.failure(LearningPathError.QUESTION_GENERATION_FAILED);
            }

            // Older blueprints of the node are kept as history; the node points to the latest one.
            AssessmentBlueprint saved = transactions.execute(status -> {
                AssessmentBlueprint stored = blueprintRepository.save(blueprint);
                path.attachBlueprint(node.getId(), stored.getId());
                learningPathRepository.save(path);
                return stored;
            });

            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not register the assessment of node {}", command.pathNodeId(), exception);
            return failures.failure(LearningPathFailures.toError(exception));
        }
    }
}
