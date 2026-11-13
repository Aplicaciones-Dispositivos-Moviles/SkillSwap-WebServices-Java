package com.innovify.skillswap.reputation.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.reputation.application.queryservices.StudentEmployabilityQueryService;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.queries.GetStudentEmployabilityByStudentIdQuery;
import com.innovify.skillswap.reputation.interfaces.rest.transform.ReputationResourceAssemblers;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The employability of a student. Only its owner can read it. */
@RestController
@RequestMapping("/api/v1/student-employability-scores")
public class StudentEmployabilityScoresController {

    private final StudentEmployabilityQueryService queryService;
    private final MessageSource messageSource;

    public StudentEmployabilityScoresController(StudentEmployabilityQueryService queryService,
                                                MessageSource messageSource) {
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /** The score of the student. 200, 403 (not the caller's) or 404 (no skill certified yet). */
    @GetMapping("/{studentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getByStudentId(@PathVariable int studentId, @AuthenticationPrincipal User actor) {
        if (actor.getId() != studentId) {
            return ReputationErrorResponses.of(messageSource, ReputationError.NOT_REPUTATION_OWNER);
        }
        var score = queryService.handle(new GetStudentEmployabilityByStudentIdQuery(studentId));
        if (score.isEmpty()) {
            return ReputationErrorResponses.of(messageSource, ReputationError.REPUTATION_NOT_FOUND);
        }
        return ResponseEntity.ok(ReputationResourceAssemblers.toResource(score.get()));
    }
}
