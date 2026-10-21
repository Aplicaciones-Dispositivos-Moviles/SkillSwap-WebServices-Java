package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.MiniProjectSubmissionQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetMiniProjectSubmissionDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.MiniProjectSubmissionResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.SubmitMiniProjectResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final MiniProjectSubmissionQueryService queryService;
    private final MessageSource messageSource;

    public MiniProjectSubmissionsController(MiniProjectSubmissionCommandService commandService,
                                            MiniProjectSubmissionQueryService queryService,
                                            MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /**
     * The student delivers the repository of the latest mini-project of an available node, before its deadline. A
     * mini-project is delivered once. The delivery is answered at once with evaluationStatus Pending: the AI evaluates
     * it against the rubric in the background and then approves it (the node is completed) or opens its verification
     * case; the result is read with the Location of the answer. 201; 400 (the link), 403 (another student's
     * mini-project), 404, or 409 (it was already delivered, the node is not available, a newer mini-project exists,
     * the deadline passed, or the node has a case in progress).
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

    /**
     * A delivery with its preliminary evaluation (status, score, confidence and feedback of the AI) and the case it
     * opened, if any. The app reads it until the status is no longer Pending. Only the student who delivered it can
     * read it. 200, 403 or 404 (also when the delivery is not of that mini-project).
     */
    @GetMapping("/{miniProjectId:\\d+}/submissions/{submissionId:\\d+}")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = MiniProjectSubmissionResource.class)))
    public ResponseEntity<?> getById(@PathVariable int miniProjectId, @PathVariable int submissionId,
                                     @AuthenticationPrincipal User actor) {
        var detail = queryService.handle(new GetMiniProjectSubmissionDetailQuery(submissionId));
        if (detail.isEmpty() || detail.get().submission().getMiniProjectId() != miniProjectId) {
            return AssessmentPeerReviewErrorResponses.of(messageSource,
                    AssessmentPeerReviewError.MINI_PROJECT_SUBMISSION_NOT_FOUND);
        }
        if (detail.get().submission().getStudentId() != actor.getId()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.NOT_SUBMISSION_OWNER);
        }
        return ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(detail.get()));
    }
}
