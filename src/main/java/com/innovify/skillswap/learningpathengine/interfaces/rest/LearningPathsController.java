package com.innovify.skillswap.learningpathengine.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.application.queryservices.LearningPathQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.ResumeLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetLearningPathsByStudentIdQuery;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.DeclareGoalResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.LearningPathResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.DeclareGoalCommandFromResourceAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathActionResultAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathResourceFromEntityAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Learning paths. Every endpoint needs a valid token (see SecurityConfig). How many paths a student keeps depends
 * on their plan: a request over the limit answers 409 PlanLimitReached with the members limit (ActiveRoutes or
 * TotalRoutes), plan, max, current and upgradeAvailable, so the app can show the screen of the limit.
 */
@RestController
@RequestMapping("/api/v1/learning-paths")
public class LearningPathsController {

    private final LearningPathCommandService commandService;
    private final LearningPathQueryService queryService;
    private final SkillTaxonomy skillTaxonomy;
    private final MessageSource messageSource;

    public LearningPathsController(LearningPathCommandService commandService, LearningPathQueryService queryService,
                                   SkillTaxonomy skillTaxonomy, MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.skillTaxonomy = skillTaxonomy;
        this.messageSource = messageSource;
    }

    /**
     * Declares a career goal in free text. The goal is interpreted against the skill taxonomy (by Gemini, choosing
     * only skills of the catalog, or by keywords when Gemini is not available), the skills the student already
     * demonstrated are discounted, and the path is built in prerequisite order. A skill covered by a certificate
     * validated by a verifier keeps its node, completed and linked to the certificate. The student is
     * the authenticated user, and the plan must allow another active path and another path in total (free: 1
     * active and 3 in total; monthly: 3 active and no total cap). 201 with the path; 400 (empty or longer than 500
     * characters), 409 (PlanLimitReached, or every skill was already demonstrated) or 422 (the goal matches no
     * skill).
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "201",
            content = @Content(schema = @Schema(implementation = LearningPathResource.class)))
    public ResponseEntity<?> declareGoal(@RequestBody DeclareGoalResource resource,
                                         @AuthenticationPrincipal User actor) {
        var command = DeclareGoalCommandFromResourceAssembler.toCommandFromResource(resource, actor.getId());
        var result = commandService.handle(command);
        return LearningPathActionResultAssembler.toResponse(result,
                path -> ResponseEntity.created(URI.create("/api/v1/learning-paths/" + path.getStudentId()))
                        .body(toResource(path)));
    }

    /**
     * The latest path of a student with the state of each node. Only the student can read it. The certificates
     * uploaded since the path was created are linked to the matching nodes first (as supporting evidence only).
     */
    @GetMapping("/{studentId:\\d+}")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = LearningPathResource.class)))
    public ResponseEntity<?> getLearningPathByStudentId(@PathVariable int studentId,
                                                        @AuthenticationPrincipal User actor) {
        if (actor.getId() != studentId) {
            return error(LearningPathError.NOT_PATH_OWNER);
        }

        var refreshed = commandService.handle(new RefreshCertificateLinksCommand(studentId));
        return LearningPathActionResultAssembler.toResponse(refreshed, path -> ResponseEntity.ok(toResource(path)));
    }

    /** Every path of the student (active, paused and completed), newest first. Only the student can read them. */
    @GetMapping
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LearningPathResource.class))))
    public ResponseEntity<?> getLearningPathsByStudentId(@RequestParam int studentId,
                                                         @AuthenticationPrincipal User actor) {
        if (actor.getId() != studentId) {
            return error(LearningPathError.NOT_PATH_OWNER);
        }
        return ResponseEntity.ok(queryService.handle(new GetLearningPathsByStudentIdQuery(studentId)).stream()
                .map(this::toResource)
                .toList());
    }

    /**
     * Pauses an active path of the caller. It keeps its progress, and its reviews in progress still count, but no
     * new assessment can be started on it. 200; 403, 404 or 409 (PathNotActive).
     */
    @PatchMapping("/{pathId:\\d+}/pause")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = LearningPathResource.class)))
    public ResponseEntity<?> pause(@PathVariable int pathId, @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new PauseLearningPathCommand(pathId, actor.getId()));
        return LearningPathActionResultAssembler.toResponse(result, path -> ResponseEntity.ok(toResource(path)));
    }

    /**
     * Makes a paused path of the caller active again, if the plan allows another active path; on the free plan,
     * pause the active one first. 200; 403, 404 or 409 (PathNotPaused or PlanLimitReached).
     */
    @PatchMapping("/{pathId:\\d+}/resume")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = LearningPathResource.class)))
    public ResponseEntity<?> resume(@PathVariable int pathId, @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new ResumeLearningPathCommand(pathId, actor.getId()));
        return LearningPathActionResultAssembler.toResponse(result, path -> ResponseEntity.ok(toResource(path)));
    }

    private LearningPathResource toResource(LearningPath path) {
        return LearningPathResourceFromEntityAssembler.toResourceFromEntity(path, skillTaxonomy::nameOf);
    }

    private ResponseEntity<?> error(LearningPathError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return LearningPathActionResultAssembler.toError(error, message);
    }
}
