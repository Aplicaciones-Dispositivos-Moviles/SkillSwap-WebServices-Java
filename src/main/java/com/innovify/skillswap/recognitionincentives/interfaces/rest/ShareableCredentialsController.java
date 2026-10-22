package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.recognitionincentives.application.commandservices.ShareableCredentialCommandService;
import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialQueryService;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateShareableCredentialCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetShareableCredentialsByOwnerQuery;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.resources.CreateShareableCredentialResource;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesActionResultAssembler;
import com.innovify.skillswap.recognitionincentives.interfaces.rest.transform.RecognitionIncentivesResourceAssemblers;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public links of the verified skills of the student, to share them outside the platform. */
@RestController
@RequestMapping("/api/v1/shareable-credentials")
@PreAuthorize("hasRole('STUDENT')")
public class ShareableCredentialsController {

    private final ShareableCredentialCommandService commandService;
    private final ShareableCredentialQueryService queryService;

    public ShareableCredentialsController(ShareableCredentialCommandService commandService,
                                          ShareableCredentialQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /**
     * Generates the link of a skill the student verified, or returns the one they already have (one per skill).
     * 201 (generated now), 200 (it already existed), 400 (no skill), 409 (the skill is not verified).
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateShareableCredentialResource resource,
                                    @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new CreateShareableCredentialCommand(actor.getId(),
                resource == null ? null : resource.skillTag()));
        return RecognitionIncentivesActionResultAssembler.toResponse(result, issued -> {
            var body = RecognitionIncentivesResourceAssemblers.toResource(issued.credential());
            return issued.created()
                    ? ResponseEntity.created(URI.create(issued.credential().url())).body(body)
                    : ResponseEntity.ok(body);
        });
    }

    /** The links of the student, from the newest to the oldest. */
    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(queryService.handle(new GetShareableCredentialsByOwnerQuery(actor.getId())).stream()
                .map(RecognitionIncentivesResourceAssemblers::toResource)
                .toList());
    }
}
