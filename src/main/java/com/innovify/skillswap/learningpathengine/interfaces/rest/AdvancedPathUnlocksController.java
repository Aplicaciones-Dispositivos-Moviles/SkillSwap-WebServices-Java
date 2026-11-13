package com.innovify.skillswap.learningpathengine.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AdvancedPathUnlock;
import com.innovify.skillswap.learningpathengine.domain.model.commands.SyncAdvancedPathUnlocksCommand;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.AdvancedPathUnlockResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathActionResultAssembler;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The advanced paths the caller redeemed with SkillCredits (POST /api/v1/credit-transactions/redeem with
 * AdvancedPathUnlock). An available unlock starts an advanced path that does not count toward the limits of the plan.
 */
@RestController
@RequestMapping("/api/v1/advanced-path-unlocks")
public class AdvancedPathUnlocksController {

    private final LearningPathCommandService commandService;

    public AdvancedPathUnlocksController(LearningPathCommandService commandService) {
        this.commandService = commandService;
    }

    /** The unlocks of the caller, oldest first; any redemption not granted yet is granted first. 200. */
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = AdvancedPathUnlockResource.class))))
    public ResponseEntity<?> getMyUnlocks(@AuthenticationPrincipal User actor) {
        var result = commandService.handle(new SyncAdvancedPathUnlocksCommand(actor.getId()));
        return LearningPathActionResultAssembler.toResponse(result,
                unlocks -> ResponseEntity.ok(unlocks.stream().map(AdvancedPathUnlocksController::toResource).toList()));
    }

    private static AdvancedPathUnlockResource toResource(AdvancedPathUnlock unlock) {
        return new AdvancedPathUnlockResource(unlock.getId(), unlock.getRedemptionId(),
                unlock.isAvailable() ? "Available" : "Used", unlock.getLearningPathId(), unlock.getGrantedAt(),
                unlock.getUsedAt());
    }
}
