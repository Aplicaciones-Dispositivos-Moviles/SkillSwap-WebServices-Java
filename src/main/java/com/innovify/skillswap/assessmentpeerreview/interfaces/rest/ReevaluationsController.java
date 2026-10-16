package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.ReevaluationCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.OrderReevaluationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.OrderReevaluationResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.ReevaluationResource;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reevaluations (US37). A Verificador senior who doubts an approved result orders the student to demonstrate the skill
 * again. Every endpoint needs a valid token.
 */
@RestController
@RequestMapping("/api/v1/reevaluations")
public class ReevaluationsController {

    private final ReevaluationCommandService commandService;

    public ReevaluationsController(ReevaluationCommandService commandService) {
        this.commandService = commandService;
    }

    /**
     * A Verificador senior orders the reevaluation of a passed attempt or of an approved case, with a justification.
     * The node of the student goes back to available (the nodes that depended on it stay unlocked) and the student
     * generates a new assessment; an approved case is reopened and given to another verifier. 201; 400 (source type
     * or justification), 403 (not a senior, or their own result), 404 or 409 (the result is not approved, the node
     * cannot be reopened, or it has a case in progress).
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "201",
            content = @Content(schema = @Schema(implementation = ReevaluationResource.class)))
    public ResponseEntity<?> order(@RequestBody(required = false) OrderReevaluationResource resource,
                                   @AuthenticationPrincipal User actor) {
        var command = new OrderReevaluationCommand(actor.getId(), parseSourceType(resource),
                resource == null || resource.sourceId() == null ? 0 : resource.sourceId(),
                resource == null ? null : resource.justification());
        var result = commandService.handle(command);
        return AssessmentPeerReviewActionResultAssembler.toResponse(result, reevaluation -> ResponseEntity
                .created(URI.create("/api/v1/reevaluations/" + reevaluation.getId()))
                .body(AssessmentPeerReviewResourceAssemblers.toResource(reevaluation)));
    }

    private static ReevaluationSourceType parseSourceType(OrderReevaluationResource resource) {
        if (resource == null || resource.sourceType() == null) {
            return null;
        }
        try {
            return ReevaluationSourceType.fromValue(resource.sourceType().strip());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
