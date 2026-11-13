package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.ReviewDeadlinePolicyCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.ReviewDeadlinePolicyQueryService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.ReviewDeadlinePolicyQueryService.EffectiveReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.DefineReviewDeadlinesCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetReviewDeadlinePoliciesQuery;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.DefineReviewDeadlinesResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.ReviewDeadlinePolicyResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The time verifiers have to resolve the cases of each plan (US39). A Verificador senior defines it; it applies to the
 * cases opened from then on, and a case whose deadline passes is reassigned to another verifier.
 */
@RestController
@RequestMapping("/api/v1/review-deadline-policies")
public class ReviewDeadlinePoliciesController {

    private final ReviewDeadlinePolicyCommandService commandService;
    private final ReviewDeadlinePolicyQueryService queryService;

    public ReviewDeadlinePoliciesController(ReviewDeadlinePolicyCommandService commandService,
                                            ReviewDeadlinePolicyQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** The deadline that applies now to each plan, monthly first. Any authenticated user can read it. 200. */
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = ReviewDeadlinePolicyResource.class))))
    public ResponseEntity<?> getPolicies() {
        return ResponseEntity.ok(toResources(queryService.handle(new GetReviewDeadlinePoliciesQuery())));
    }

    /**
     * A Verificador senior defines the deadline of each plan: up to 48 hours for the monthly plan and up to 5
     * business days for the free plan. 200 with both; 400 (missing or out of range) or 403 (not a senior).
     */
    @PutMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = ReviewDeadlinePolicyResource.class))))
    public ResponseEntity<?> definePolicies(@RequestBody(required = false) DefineReviewDeadlinesResource resource,
                                            @AuthenticationPrincipal User actor) {
        var command = new DefineReviewDeadlinesCommand(actor.getId(),
                resource == null ? null : resource.premiumPlanHours(),
                resource == null ? null : resource.freePlanBusinessDays());
        var result = commandService.handle(command);
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                policies -> ResponseEntity.ok(toResources(queryService.handle(new GetReviewDeadlinePoliciesQuery()))));
    }

    private static List<ReviewDeadlinePolicyResource> toResources(List<EffectiveReviewDeadline> deadlines) {
        return deadlines.stream()
                .map(effective -> new ReviewDeadlinePolicyResource(effective.plan(), effective.deadline().amount(),
                        effective.deadline().unit().value(), effective.policy() != null,
                        effective.policy() == null ? null : effective.policy().getUpdatedByUserId(),
                        effective.policy() == null ? null : effective.policy().getUpdatedAt()))
                .toList();
    }
}
