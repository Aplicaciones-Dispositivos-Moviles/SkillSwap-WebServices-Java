package com.innovify.skillswap.learningpathengine.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.CertificateLinkResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.LinkCertificateResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.CertificateLinkResourceFromOutcomeAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathActionResultAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LinkCertificateToNodeCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Certificates as evidence of the nodes of a learning path. Every endpoint needs a valid token. */
@RestController
@RequestMapping("/api/v1/path-nodes")
public class PathNodeCertificatesController {

    private final LearningPathCommandService commandService;
    private final SkillTaxonomy skillTaxonomy;

    public PathNodeCertificatesController(LearningPathCommandService commandService, SkillTaxonomy skillTaxonomy) {
        this.commandService = commandService;
        this.skillTaxonomy = skillTaxonomy;
    }

    /**
     * Associates one of the caller's certificates with a node of their path. The content extracted from the
     * certificate (course name and OCR text) is compared with the skill of the node; when the affinity reaches
     * the threshold (0.7) the certificate is linked to the node, and its practical assessment can be requested
     * once the node is available. 200 with the affinity; 403 (another student's node or certificate), 404 (node or
     * certificate), 409 (CertificateNotVerified: pending, suspicious or rejected; or NodeAlreadyCompleted) or 422
     * (CertificateSkillMismatch, with affinity, threshold and suggestedNodes: the nodes of the path the certificate
     * does cover, best first). Nothing changes on an error.
     */
    @PostMapping("/{nodeId:\\d+}/certificate")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = CertificateLinkResource.class)))
    public ResponseEntity<?> linkCertificate(@PathVariable int nodeId, @RequestBody LinkCertificateResource resource,
                                             @AuthenticationPrincipal User actor) {
        var command = LinkCertificateToNodeCommandFromResourceAssembler.toCommandFromResource(nodeId, resource,
                actor.getId());
        var result = commandService.handle(command);
        return LearningPathActionResultAssembler.toResponse(result, outcome -> ResponseEntity.ok(
                CertificateLinkResourceFromOutcomeAssembler.toResourceFromOutcome(outcome, skillTaxonomy::nameOf)));
    }
}
