package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerifierProfileCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerifierSuspensionCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerifierProfileQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CreateVerifierProfileCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReinstateVerifierCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SuspendVerifierCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.UpdateVerifierAvailabilityCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerifierProfileByUserIdQuery;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.CreateVerifierProfileResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.SuspendVerifierResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.UpdateVerifierAvailabilityResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The verifier profile of the caller. Being a verifier is not a role: a student acquires the profile by
 * completing a skill. Every endpoint needs a valid token and acts on the caller's own profile, except the
 * suspension and reinstatement, which a Verificador senior does on the profile of another verifier.
 */
@RestController
@RequestMapping("/api/v1/verifier-profiles")
public class VerifierProfilesController {

    private final VerifierProfileCommandService commandService;
    private final VerifierProfileQueryService queryService;
    private final VerifierSuspensionCommandService suspensionService;
    private final MessageSource messageSource;

    public VerifierProfilesController(VerifierProfileCommandService commandService,
                                      VerifierProfileQueryService queryService,
                                      VerifierSuspensionCommandService suspensionService,
                                      MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.suspensionService = suspensionService;
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

    /**
     * A Verificador senior suspends a verifier whose reliability is below 60: they receive no more cases, and the
     * cases they had in progress go to other verifiers. Body: {@code {"justification"}} (required, up to 2000
     * characters). 200 with the profile; 400 (JustificationRequired or JustificationTooLong), 403 (NotSeniorVerifier),
     * 404 (VerifierProfileNotFound) or 409 (CannotSuspendSelf, AlreadySuspended or NotLowReliability).
     */
    @PostMapping("/{verifierUserId:\\d+}/suspension")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> suspend(@PathVariable int verifierUserId,
                                     @RequestBody(required = false) SuspendVerifierResource resource,
                                     @AuthenticationPrincipal User actor) {
        String justification = resource == null || resource.justification() == null ? "" : resource.justification();
        var result = suspensionService.handle(new SuspendVerifierCommand(actor.getId(), verifierUserId, justification));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                profile -> ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(profile)));
    }

    /**
     * A Verificador senior reinstates a suspended verifier, who receives cases again. 200 with the profile; 403
     * (NotSeniorVerifier), 404 (VerifierProfileNotFound) or 409 (NotSuspended).
     */
    @DeleteMapping("/{verifierUserId:\\d+}/suspension")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> reinstate(@PathVariable int verifierUserId, @AuthenticationPrincipal User actor) {
        var result = suspensionService.handle(new ReinstateVerifierCommand(actor.getId(), verifierUserId));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result,
                profile -> ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(profile)));
    }
}
