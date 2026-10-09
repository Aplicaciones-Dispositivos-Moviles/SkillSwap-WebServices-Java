package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerificationCaseCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseQueryService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerifierProfileQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AppealVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AttachCaseEvidenceCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ResolveVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCasesByVerifierQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerifierProfileByUserIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.AttachCaseEvidenceResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.ResolveVerificationCaseResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerificationCaseDetailResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerificationCaseResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verification cases. There is no global listing: the student reads and changes their own case, and the
 * verifier works on the cases assigned to them. Every endpoint needs a valid token. The endpoints answer
 * {@code ResponseEntity<?>} (the resource or a problem), so the success body is declared for the documentation.
 */
@RestController
@RequestMapping("/api/v1/verification-cases")
public class VerificationCasesController {

    private final VerificationCaseCommandService commandService;
    private final VerificationCaseQueryService queryService;
    private final VerifierProfileQueryService profileQueryService;
    private final MessageSource messageSource;

    public VerificationCasesController(VerificationCaseCommandService commandService,
                                       VerificationCaseQueryService queryService,
                                       VerifierProfileQueryService profileQueryService,
                                       MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.profileQueryService = profileQueryService;
        this.messageSource = messageSource;
    }

    /**
     * The cases assigned to the caller, newest first. Only a verifier can ask for them. 200 or 403.
     */
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = VerificationCaseResource.class))))
    public ResponseEntity<?> getAssignedCases(@AuthenticationPrincipal User actor) {
        var profile = profileQueryService.handle(new GetVerifierProfileByUserIdQuery(actor.getId()));
        if (profile.isEmpty() || !profile.get().isVerified()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.NOT_A_VERIFIER);
        }

        var cases = queryService.handle(new GetVerificationCasesByVerifierQuery(actor.getId())).stream()
                .map(AssessmentPeerReviewResourceAssemblers::toResource)
                .toList();
        return ResponseEntity.ok(cases);
    }

    /**
     * A case with its attempt and the questions that were failed (never the correct answers). Only the student
     * of the case and the verifier it is assigned to can read it. 200, 403 or 404.
     */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = VerificationCaseDetailResource.class)))
    public ResponseEntity<?> getById(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var detail = queryService.handle(new GetVerificationCaseDetailQuery(id));
        if (detail.isEmpty()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.CASE_NOT_FOUND);
        }

        var verificationCase = detail.get().verificationCase();
        boolean isParty = verificationCase.getStudentId() == actor.getId() || verificationCase.isAssignedTo(actor.getId());
        if (!isParty) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.NOT_CASE_OWNER);
        }
        return ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(detail.get()));
    }

    /**
     * The student of the case attaches (or replaces) the link to their repository or portfolio while the case is
     * open. 200; 400 (not an http or https link), 403, 404 or 409 (already resolved).
     */
    @PutMapping("/{id:\\d+}/evidence")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = VerificationCaseResource.class)))
    public ResponseEntity<?> attachEvidence(@PathVariable int id, @RequestBody AttachCaseEvidenceResource resource,
                                            @AuthenticationPrincipal User actor) {
        String url = resource == null || resource.evidenceUrl() == null ? "" : resource.evidenceUrl();
        var result = commandService.handle(new AttachCaseEvidenceCommand(id, actor.getId(), url));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                verificationCase -> ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(verificationCase)));
    }

    /**
     * The assigned verifier resolves the case with a decision and the notes of the rubric; an approval completes
     * the node of the student. 200; 400 (decision or notes), 403 (not the assigned verifier, or no verifier
     * profile), 404 or 409 (already resolved).
     */
    @PatchMapping("/{id:\\d+}/decision")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = VerificationCaseResource.class)))
    public ResponseEntity<?> resolve(@PathVariable int id, @RequestBody ResolveVerificationCaseResource resource,
                                     @AuthenticationPrincipal User actor) {
        ReviewDecision decision = resource == null ? null : ReviewDecision.tryParse(resource.decision()).orElse(null);
        String notes = resource == null || resource.rubricNotes() == null ? "" : resource.rubricNotes();

        var result = commandService.handle(new ResolveVerificationCaseCommand(id, actor.getId(), decision, notes));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                verificationCase -> ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(verificationCase)));
    }

    /**
     * The student appeals a rejected case, once. It reopens and goes to a different verifier (never the student
     * nor the one who rejected it); with nobody available it waits as Pending. 200; 403, 404 or 409 (the case is
     * not rejected, it was already appealed, or the node has another case open).
     */
    @PostMapping("/{id:\\d+}/appeal")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = VerificationCaseResource.class)))
    public ResponseEntity<?> appeal(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new AppealVerificationCaseCommand(id, actor.getId()));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                verificationCase -> ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(verificationCase)));
    }
}
