package com.innovify.skillswap.learningpathengine.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.DeclareGoalResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.LearningPathResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.DeclareGoalCommandFromResourceAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathActionResultAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathResourceFromEntityAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learning paths. Every endpoint needs a valid token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/learning-paths")
public class LearningPathsController {

    private final LearningPathCommandService commandService;
    private final SkillTaxonomy skillTaxonomy;
    private final MessageSource messageSource;

    public LearningPathsController(LearningPathCommandService commandService, SkillTaxonomy skillTaxonomy,
                                   MessageSource messageSource) {
        this.commandService = commandService;
        this.skillTaxonomy = skillTaxonomy;
        this.messageSource = messageSource;
    }

    /**
     * Declares a career goal in free text. The goal is interpreted against the skill taxonomy, the skills the
     * student already demonstrated are discounted, and the path is built in prerequisite order. The student is
     * the authenticated user. 201 with the path; 400 (empty or longer than 500 characters), 409 (an active path
     * exists, or every skill was already demonstrated) or 422 (the goal matches no skill).
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
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
    public ResponseEntity<?> getLearningPathByStudentId(@PathVariable int studentId,
                                                        @AuthenticationPrincipal User actor) {
        if (actor.getId() != studentId) {
            return error(LearningPathError.NOT_PATH_OWNER);
        }

        var refreshed = commandService.handle(new RefreshCertificateLinksCommand(studentId));
        return LearningPathActionResultAssembler.toResponse(refreshed, path -> ResponseEntity.ok(toResource(path)));
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
