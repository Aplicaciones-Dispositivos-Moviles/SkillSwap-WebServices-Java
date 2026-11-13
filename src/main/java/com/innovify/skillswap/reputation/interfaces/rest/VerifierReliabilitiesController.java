package com.innovify.skillswap.reputation.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.reputation.application.queryservices.VerifierReliabilityQueryService;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierReliabilityByUserIdQuery;
import com.innovify.skillswap.reputation.interfaces.rest.transform.ReputationResourceAssemblers;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The reliability of a verifier. Only the verifier can read it. */
@RestController
@RequestMapping("/api/v1/verifier-reliabilities")
public class VerifierReliabilitiesController {

    private final VerifierReliabilityQueryService queryService;
    private final MessageSource messageSource;

    public VerifierReliabilitiesController(VerifierReliabilityQueryService queryService,
                                           MessageSource messageSource) {
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /** The reliability of the verifier. 200, 403 (not the caller's) or 404 (no case resolved yet). */
    @GetMapping("/{verifierUserId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getByVerifierUserId(@PathVariable int verifierUserId,
                                                 @AuthenticationPrincipal User actor) {
        if (actor.getId() != verifierUserId) {
            return ReputationErrorResponses.of(messageSource, ReputationError.NOT_REPUTATION_OWNER);
        }
        var reliability = queryService.handle(new GetVerifierReliabilityByUserIdQuery(verifierUserId));
        if (reliability.isEmpty()) {
            return ReputationErrorResponses.of(messageSource, ReputationError.REPUTATION_NOT_FOUND);
        }
        return ResponseEntity.ok(ReputationResourceAssemblers.toResource(reliability.get()));
    }
}
