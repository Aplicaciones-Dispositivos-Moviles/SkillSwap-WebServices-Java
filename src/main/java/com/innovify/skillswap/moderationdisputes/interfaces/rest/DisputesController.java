package com.innovify.skillswap.moderationdisputes.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.moderationdisputes.application.commandservices.DisputeCommandService;
import com.innovify.skillswap.moderationdisputes.application.queryservices.DisputeQueryService;
import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.ResolveDisputeCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.queries.GetDisputeByIdQuery;
import com.innovify.skillswap.moderationdisputes.domain.model.queries.GetDisputesByReviewerQuery;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.resources.DisputeEvidenceResource;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.resources.DisputeResource;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.resources.ResolveDisputeResource;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.transform.DisputeResourceAssemblers;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.transform.ModerationDisputesActionResultAssembler;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disputes: the suspicious certificates escalated to a Verificador senior (US13, US14). There is no global listing:
 * a reviewer only sees and resolves the disputes assigned to them, which never include their own certificates.
 * Every endpoint needs a valid token.
 */
@RestController
@RequestMapping("/api/v1/disputes")
public class DisputesController {

    private final DisputeCommandService commandService;
    private final DisputeQueryService queryService;
    private final VerifierProfileContextFacade verifierFacade;
    private final MessageSource messageSource;

    public DisputesController(DisputeCommandService commandService, DisputeQueryService queryService,
                              VerifierProfileContextFacade verifierFacade, MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.verifierFacade = verifierFacade;
        this.messageSource = messageSource;
    }

    /**
     * The disputes assigned to the caller, oldest first: the pending ones unless {@code status} asks for Resolved
     * (or All). Only an enabled verifier can ask for them. 200; 400 (unknown status) or 403.
     */
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DisputeResource.class))))
    public ResponseEntity<?> getAssignedDisputes(@RequestParam(required = false) String status,
                                                 @AuthenticationPrincipal User actor) {
        DisputeStatus filter = null;
        if (status == null || status.isBlank()) {
            filter = DisputeStatus.PENDING;
        } else if (!"All".equalsIgnoreCase(status.strip())) {
            filter = DisputeStatus.tryParse(status).orElse(null);
            if (filter == null) {
                return ModerationDisputesErrorResponses.of(messageSource,
                        ModerationDisputesError.INVALID_DISPUTE_STATUS);
            }
        }
        if (!verifierFacade.isEnabledVerifier(actor.getId())) {
            return ModerationDisputesErrorResponses.of(messageSource, ModerationDisputesError.NOT_A_VERIFIER);
        }

        return ResponseEntity.ok(queryService.handle(new GetDisputesByReviewerQuery(actor.getId(), filter)).stream()
                .map(DisputeResourceAssemblers::toResource)
                .toList());
    }

    /**
     * The dispute with the certificate under review: the data read by the OCR, its risk and a temporary link to the
     * file. Only its reviewer can read it. 200, 403 or 404.
     */
    @GetMapping("/{id:\\d+}/evidence")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = DisputeEvidenceResource.class)))
    public ResponseEntity<?> getEvidence(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var dispute = queryService.handle(new GetDisputeByIdQuery(id));
        if (dispute.isEmpty()) {
            return ModerationDisputesErrorResponses.of(messageSource, ModerationDisputesError.DISPUTE_NOT_FOUND);
        }
        if (!dispute.get().isAssignedTo(actor.getId())) {
            return ModerationDisputesErrorResponses.of(messageSource,
                    ModerationDisputesError.NOT_ASSIGNED_REVIEWER);
        }
        var certificate = queryService.getCertificateEvidence(dispute.get()).orElse(null);
        return ResponseEntity.ok(DisputeResourceAssemblers.toResource(dispute.get(), certificate));
    }

    /**
     * The reviewer resolves the dispute with the outcome and the required observations. For a certificate review,
     * Upheld verifies the certificate and Overturned rejects it. 200; 400 (outcome or observations), 403 (not the
     * reviewer), 404 or 409 (already resolved, or the certificate is no longer suspicious).
     */
    @PatchMapping("/{id:\\d+}/resolve")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DisputeResource.class)))
    public ResponseEntity<?> resolve(@PathVariable int id, @RequestBody(required = false) ResolveDisputeResource resource,
                                     @AuthenticationPrincipal User actor) {
        DisputeOutcome outcome = resource == null ? null : DisputeOutcome.tryParse(resource.outcome()).orElse(null);
        String notes = resource == null || resource.resolutionNotes() == null ? "" : resource.resolutionNotes();

        var result = commandService.handle(new ResolveDisputeCommand(id, actor.getId(), outcome, notes));
        return ModerationDisputesActionResultAssembler.toResponse(result,
                dispute -> ResponseEntity.ok(DisputeResourceAssemblers.toResource(dispute)));
    }
}
