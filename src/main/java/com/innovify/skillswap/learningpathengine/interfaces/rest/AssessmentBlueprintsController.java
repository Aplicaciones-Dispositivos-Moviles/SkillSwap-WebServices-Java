package com.innovify.skillswap.learningpathengine.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.learningpathengine.application.commandservices.AssessmentBlueprintCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateAssessmentBlueprintCommand;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.AssessmentBlueprintResourceFromEntityAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathActionResultAssembler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Assessments of the nodes of a learning path. Every endpoint needs a valid token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/path-nodes")
public class AssessmentBlueprintsController {

    private final AssessmentBlueprintCommandService commandService;
    private final SkillTaxonomy skillTaxonomy;

    public AssessmentBlueprintsController(AssessmentBlueprintCommandService commandService,
                                          SkillTaxonomy skillTaxonomy) {
        this.commandService = commandService;
        this.skillTaxonomy = skillTaxonomy;
    }

    /**
     * Generates with AI the assessment of an available node. Only the owner of the path can request it. The
     * response contains the questions but never their correct answers, and requesting it again generates a new
     * assessment. 201; 403 (another student's node), 404, 409 (locked, listing the pending prerequisites, or
     * already completed) or 503 (the AI service failed; the node is unchanged).
     */
    @PostMapping("/{nodeId:\\d+}/assessment-blueprint")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> generateAssessmentBlueprint(@PathVariable int nodeId,
                                                         @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new GenerateAssessmentBlueprintCommand(nodeId, actor.getId()));
        return LearningPathActionResultAssembler.toResponse(result,
                blueprint -> ResponseEntity.status(HttpStatus.CREATED).body(
                        AssessmentBlueprintResourceFromEntityAssembler.toResourceFromEntity(blueprint,
                                skillTaxonomy::nameOf)));
    }
}
