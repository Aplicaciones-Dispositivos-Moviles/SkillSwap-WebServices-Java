package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.AssessmentAttemptCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.AssessmentAttemptQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitAssessmentAttemptCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.AssessmentAttemptResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.SubmitAssessmentAttemptResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.List;
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

/** The attempts of the assessments. Every endpoint needs a valid token and acts on the caller's own data. */
@RestController
@RequestMapping("/api/v1/assessment-attempts")
public class AssessmentAttemptsController {

    private final AssessmentAttemptCommandService commandService;
    private final AssessmentAttemptQueryService queryService;
    private final MessageSource messageSource;

    public AssessmentAttemptsController(AssessmentAttemptCommandService commandService,
                                        AssessmentAttemptQueryService queryService, MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /**
     * Submits the answers of an assessment. The server grades them: a passing attempt completes the node; a
     * failing one opens a verification case, which goes to a verifier right away when one is available and is due
     * by the review deadline of the plan (48 hours monthly, 5 business days free). When the student already used
     * the escalations of the month of their plan (10 monthly, 3 free), the attempt is recorded but no case is
     * opened, and planLimitReached says so. The student is the authenticated user. 201 with the attempt (and the
     * case it opened); 400 (answers that do not match the questions), 403 (another student's assessment), 404 (no
     * such assessment) or 409 (an outdated assessment, one already answered, a node that is not available or whose
     * path is paused, or a case still open).
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "201",
            content = @Content(schema = @Schema(implementation = AssessmentAttemptResource.class)))
    public ResponseEntity<?> submit(@RequestBody SubmitAssessmentAttemptResource resource,
                                    @AuthenticationPrincipal User actor) {
        int blueprintId = resource == null || resource.blueprintId() == null ? 0 : resource.blueprintId();
        List<Integer> answers = resource == null || resource.selectedAnswers() == null
                ? List.of() : resource.selectedAnswers();

        var result = commandService.handle(new SubmitAssessmentAttemptCommand(actor.getId(), blueprintId, answers));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                outcome -> ResponseEntity.created(URI.create("/api/v1/assessment-attempts/" + outcome.attempt().getId()))
                        .body(AssessmentPeerReviewResourceAssemblers.toResource(outcome)));
    }

    /** An attempt. Only its student can read it. 200, 403 or 404. */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getById(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var attempt = queryService.handle(new GetAssessmentAttemptByIdQuery(id));
        if (attempt.isEmpty()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.ATTEMPT_NOT_FOUND);
        }
        if (attempt.get().getStudentId() != actor.getId()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.NOT_ATTEMPT_OWNER);
        }
        return ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(attempt.get()));
    }
}
