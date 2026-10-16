package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import com.innovify.skillswap.learningpathengine.application.commandservices.MiniProjectCommandService;
import com.innovify.skillswap.learningpathengine.application.commandservices.MiniProjectGeneration;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateMiniProjectCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.MiniProjectDraft;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.learningpathengine.domain.repositories.MiniProjectRepository;
import com.innovify.skillswap.learningpathengine.domain.services.MiniProjectGenerationService;
import com.innovify.skillswap.shared.application.Result;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/**
 * Mini-project command service. Like the other command services it is not {@code @Transactional}: generating the
 * mini-project (a slow call to an external provider) must not hold a transaction, and the only write is a single
 * save.
 *
 * <p>A node can have a mini-project while it is available and its path is active. The latest one is returned again
 * while it can still be delivered; a new one is generated when there is none, the latest expired, or the student
 * asks for it. Older mini-projects are kept as history.
 */
@Service
public class MiniProjectCommandServiceImpl implements MiniProjectCommandService {

    private static final Logger log = LoggerFactory.getLogger(MiniProjectCommandServiceImpl.class);

    private final LearningPathRepository learningPathRepository;
    private final MiniProjectRepository miniProjectRepository;
    private final MiniProjectGenerationService generationService;
    private final LearningPathFailures failures;

    public MiniProjectCommandServiceImpl(LearningPathRepository learningPathRepository,
                                         MiniProjectRepository miniProjectRepository,
                                         MiniProjectGenerationService generationService,
                                         MessageSource messageSource) {
        this.learningPathRepository = learningPathRepository;
        this.miniProjectRepository = miniProjectRepository;
        this.generationService = generationService;
        this.failures = new LearningPathFailures(messageSource);
    }

    @Override
    public Result<MiniProjectGeneration> handle(GenerateMiniProjectCommand command) {
        try {
            Optional<LearningPath> found = learningPathRepository.findByNodeId(command.pathNodeId());
            if (found.isEmpty()) {
                return failures.failure(LearningPathError.NODE_NOT_FOUND);
            }
            LearningPath path = found.get();

            // Only the owner of the path can request (and therefore see) the mini-project of a node.
            if (path.getStudentId() != command.studentId()) {
                return failures.failure(LearningPathError.NOT_PATH_OWNER);
            }
            if (path.isPaused()) {
                return failures.failure(LearningPathError.PATH_PAUSED);
            }

            PathNode node = path.getNode(command.pathNodeId()).orElseThrow();
            if (node.getStatus() == NodeStatus.COMPLETED) {
                return failures.failure(LearningPathError.NODE_ALREADY_COMPLETED);
            }
            if (node.getStatus() == NodeStatus.LOCKED) {
                return failures.failure(LearningPathError.NODE_LOCKED,
                        Map.of("pendingPrerequisites", path.pendingPrerequisitesOf(node.getId())));
            }

            if (!command.regenerate()) {
                Optional<MiniProject> latest = miniProjectRepository.findLatestByPathNodeId(node.getId());
                if (latest.isPresent() && !latest.get().isExpired(Instant.now())) {
                    return Result.success(new MiniProjectGeneration(latest.get(), false));
                }
            }

            MiniProject miniProject;
            try {
                MiniProjectDraft draft = generationService.generateMiniProject(node.getSkillTag());
                miniProject = new MiniProject(node.getId(), node.getSkillTag(), draft);
            } catch (RuntimeException exception) {
                // Provider failure, or output that does not meet the contract (a DomainException).
                log.error("Could not generate the mini-project of node {} ({})", node.getId(), node.getSkillTag(),
                        exception);
                return failures.failure(LearningPathError.MINI_PROJECT_GENERATION_FAILED);
            }

            return Result.success(new MiniProjectGeneration(miniProjectRepository.save(miniProject), true));
        } catch (RuntimeException exception) {
            log.error("Could not register the mini-project of node {}", command.pathNodeId(), exception);
            return failures.failure(LearningPathFailures.toError(exception));
        }
    }
}
