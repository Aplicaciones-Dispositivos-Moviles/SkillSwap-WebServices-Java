package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerifierProfileCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerifierProfileQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CreateVerifierProfileCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.UpdateVerifierAvailabilityCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerifierProfileByUserIdQuery;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.CreateVerifierProfileResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.UpdateVerifierAvailabilityResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The verifier profile of the caller. Being a verifier is not a role: a student acquires the profile by
 * completing a skill. Every endpoint needs a valid token and acts on the caller's own profile.
 */
@RestController
@RequestMapping("/api/v1/verifier-profiles")
public class VerifierProfilesController {

    private final VerifierProfileCommandService commandService;
    private final VerifierProfileQueryService queryService;
    private final MessageSource messageSource;

    public VerifierProfilesController(VerifierProfileCommandService commandService,
                                      VerifierProfileQueryService queryService, MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /**
     * Enables the caller as a verifier of a skill whose node they completed, creating the profile the first time.
     * Waiting cases of that skill are assigned right away. 201 with the profile; 400 (empty skill), 409 (the skill
     * was not completed, or is already enabled) or 403 (a revoked profile).
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> create(@RequestBody CreateVerifierProfileResource resource,
                                    @AuthenticationPrincipal User actor) {
        String skillTag = resource == null || resource.skillTag() == null ? "" : resource.skillTag();
        var result = commandService.handle(new CreateVerifierProfileCommand(actor.getId(), skillTag));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                profile -> ResponseEntity.created(URI.create("/api/v1/verifier-profiles/me"))
                        .body(AssessmentPeerReviewResourceAssemblers.toResource(profile)));
    }

    /** The profile of the caller. 200 or 404. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getMine(@AuthenticationPrincipal User actor) {
        var profile = queryService.handle(new GetVerifierProfileByUserIdQuery(actor.getId()));
        if (profile.isEmpty()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource,
                    AssessmentPeerReviewError.VERIFIER_PROFILE_NOT_FOUND);
        }
        return ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(profile.get()));
    }

    /** Switches the availability of the caller; turning it on puts them back in the queue. 200, 400 or 403 (no verifier profile). */
    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> updateAvailability(@RequestBody UpdateVerifierAvailabilityResource resource,
                                                @AuthenticationPrincipal User actor) {
        if (resource == null || resource.available() == null) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.INVALID_AVAILABILITY);
        }
        var result = commandService.handle(new UpdateVerifierAvailabilityCommand(actor.getId(), resource.available()));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                profile -> ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(profile)));
    }
}
