package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.MiniProjectSubmissionResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.SubmitMiniProjectResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deliveries of the mini-projects (US19). Every endpoint needs a valid token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/mini-projects")
public class MiniProjectSubmissionsController {

    private final MiniProjectSubmissionCommandService commandService;

    public MiniProjectSubmissionsController(MiniProjectSubmissionCommandService commandService) {
        this.commandService = commandService;
    }

    /**
     * The student delivers the repository of the latest mini-project of an available node, before its deadline. A
     * mini-project is delivered once. The delivery is only received here: its evaluation is done afterwards. 201; 400
     * (the link), 403 (another student's mini-project), 404, or 409 (the node is not available, a newer mini-project
     * exists, the deadline passed, the node has a case in progress, or it was already delivered).
     */
    @PostMapping("/{miniProjectId:\\d+}/submissions")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "201",
            content = @Content(schema = @Schema(implementation = MiniProjectSubmissionResource.class)))
    public ResponseEntity<?> submit(@PathVariable int miniProjectId,
                                    @RequestBody(required = false) SubmitMiniProjectResource resource,
                                    @AuthenticationPrincipal User actor) {
        var command = new SubmitMiniProjectCommand(miniProjectId, actor.getId(),
                resource == null ? null : resource.repositoryUrl());
        var result = commandService.handle(command);
        return AssessmentPeerReviewActionResultAssembler.toResponse(result, submission -> ResponseEntity
                .created(URI.create("/api/v1/mini-projects/" + miniProjectId + "/submissions/" + submission.getId()))
                .body(AssessmentPeerReviewResourceAssemblers.toResource(submission)));
    }
}
