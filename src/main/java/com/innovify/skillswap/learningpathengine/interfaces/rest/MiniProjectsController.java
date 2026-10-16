package com.innovify.skillswap.learningpathengine.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.learningpathengine.application.commandservices.MiniProjectCommandService;
import com.innovify.skillswap.learningpathengine.application.queryservices.MiniProjectQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateMiniProjectCommand;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetMiniProjectByPathNodeIdQuery;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathActionResultAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.MiniProjectResourceFromEntityAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mini-projects of the nodes of a learning path. Every endpoint needs a valid token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/path-nodes")
public class MiniProjectsController {

    private final MiniProjectCommandService commandService;
    private final MiniProjectQueryService queryService;
    private final SkillTaxonomy skillTaxonomy;
    private final MessageSource messageSource;

    public MiniProjectsController(MiniProjectCommandService commandService, MiniProjectQueryService queryService,
                                  SkillTaxonomy skillTaxonomy, MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.skillTaxonomy = skillTaxonomy;
        this.messageSource = messageSource;
    }

    /**
     * Generates with AI the mini-project of an available node, with its rubric and a deadline of 5 days to deliver
     * the repository. While the latest one can still be delivered it is returned again (200) without generating
     * another; a new one is generated (201) when there is none, the latest expired, or {@code regenerate=true} (to
     * try again after a rejected delivery). 403 (another student's node), 404, 409 (locked, listing the pending
     * prerequisites, completed or paused path) or 503 (the AI service failed).
     */
    @PostMapping("/{nodeId:\\d+}/mini-project")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> generateMiniProject(@PathVariable int nodeId,
                                                 @RequestParam(defaultValue = "false") boolean regenerate,
                                                 @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new GenerateMiniProjectCommand(nodeId, actor.getId(), regenerate));
        return LearningPathActionResultAssembler.toResponse(result, generation -> ResponseEntity
                .status(generation.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(MiniProjectResourceFromEntityAssembler.toResourceFromEntity(generation.miniProject(),
                        skillTaxonomy::nameOf)));
    }

    /** The latest mini-project of a node of the student. 404 when it has none or the node is not theirs. */
    @GetMapping("/{nodeId:\\d+}/mini-project")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getMiniProject(@PathVariable int nodeId, @AuthenticationPrincipal User actor) {
        return queryService.handle(new GetMiniProjectByPathNodeIdQuery(nodeId, actor.getId()))
                .<ResponseEntity<?>>map(miniProject -> ResponseEntity.ok(
                        MiniProjectResourceFromEntityAssembler.toResourceFromEntity(miniProject,
                                skillTaxonomy::nameOf)))
                .orElseGet(() -> error(LearningPathError.MINI_PROJECT_NOT_FOUND));
    }

    private ResponseEntity<?> error(LearningPathError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return LearningPathActionResultAssembler.toError(error, message);
    }
}
