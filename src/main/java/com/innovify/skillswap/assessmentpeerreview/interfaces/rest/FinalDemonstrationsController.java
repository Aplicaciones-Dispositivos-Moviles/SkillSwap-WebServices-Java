package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.DemonstrationEvaluationCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.FinalDemonstrationCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FinalDemonstrationQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CancelFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.RescheduleFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReviewFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ScheduleFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitDemonstrationVideoCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssignedFinalDemonstrationsQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetDemonstrationVideoQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetFinalDemonstrationsByStudentIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.DemonstrationVideoResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.FinalDemonstrationResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.RescheduleFinalDemonstrationResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.ReviewFinalDemonstrationResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.ScheduleFinalDemonstrationResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Final demonstrations of the completed paths (US28). Every endpoint needs a valid token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/final-demonstrations")
public class FinalDemonstrationsController {

    private final FinalDemonstrationCommandService commandService;
    private final DemonstrationEvaluationCommandService evaluationService;
    private final FinalDemonstrationQueryService queryService;
    private final MessageSource messageSource;

    public FinalDemonstrationsController(FinalDemonstrationCommandService commandService,
                                         DemonstrationEvaluationCommandService evaluationService,
                                         FinalDemonstrationQueryService queryService,
                                         MessageSource messageSource) {
        this.commandService = commandService;
        this.evaluationService = evaluationService;
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /**
     * The student schedules the final demonstration of a completed path: the modality (AdvancedProject or
     * SupervisedExam) and a moment at least 24 hours ahead. A verifier enabled in a skill of the path, available and
     * with the least work, is assigned. 201; 400 (modality or date missing or invalid), 403 (not the owner of the path),
     * 404 (path missing or unknown), or 409 (the path is not completed, the moment is less than 24 hours ahead, no verifier is available,
     * or the path already has a demonstration scheduled, submitted or approved).
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "201",
            content = @Content(schema = @Schema(implementation = FinalDemonstrationResource.class)))
    public ResponseEntity<?> schedule(@RequestBody(required = false) ScheduleFinalDemonstrationResource resource,
                                      @AuthenticationPrincipal User actor) {
        if (resource == null || resource.learningPathId() == null) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.LEARNING_PATH_NOT_FOUND);
        }
        // A missing or unknown modality reaches the service as null, which answers InvalidModality.
        var command = new ScheduleFinalDemonstrationCommand(actor.getId(), resource.learningPathId(),
                parseModality(resource.modality()), resource.scheduledAt());
        var result = commandService.handle(command);
        return AssessmentPeerReviewActionResultAssembler.toResponse(result, demonstration -> ResponseEntity
                .created(URI.create("/api/v1/final-demonstrations/" + demonstration.getId()))
                .body(AssessmentPeerReviewResourceAssemblers.toResource(demonstration)));
    }

    /** The final demonstrations of the student, the most recent first. 200. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = FinalDemonstrationResource.class))))
    public ResponseEntity<List<FinalDemonstrationResource>> getMine(@AuthenticationPrincipal User actor) {
        var demonstrations = queryService.handle(new GetFinalDemonstrationsByStudentIdQuery(actor.getId()));
        return ResponseEntity.ok(demonstrations.stream()
                .map(AssessmentPeerReviewResourceAssemblers::toResource)
                .toList());
    }

    /**
     * The final demonstrations assigned to the verifier that are not cancelled, the soonest first. 200 (empty when
     * the user is not a verifier).
     */
    @GetMapping("/assigned")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = FinalDemonstrationResource.class))))
    public ResponseEntity<List<FinalDemonstrationResource>> getAssigned(@AuthenticationPrincipal User actor) {
        var demonstrations = queryService.handle(new GetAssignedFinalDemonstrationsQuery(actor.getId()));
        return ResponseEntity.ok(demonstrations.stream()
                .map(AssessmentPeerReviewResourceAssemblers::toResource)
                .toList());
    }

    /** A demonstration. Only the student and the verifier it is assigned to can read it. 200, 403 or 404. */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FinalDemonstrationResource.class)))
    public ResponseEntity<?> getById(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var found = queryService.handle(new GetFinalDemonstrationByIdQuery(id));
        if (found.isEmpty()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource,
                    AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
        }
        var demonstration = found.get();
        if (!demonstration.isOwnedBy(actor.getId()) && !demonstration.isAssignedTo(actor.getId())) {
            return AssessmentPeerReviewErrorResponses.of(messageSource,
                    AssessmentPeerReviewError.NOT_DEMONSTRATION_PARTICIPANT);
        }
        return ResponseEntity.ok(AssessmentPeerReviewResourceAssemblers.toResource(demonstration));
    }

    /**
     * The student moves a scheduled demonstration to another moment, at least 24 hours ahead; the verifier stays.
     * 200; 400 (date missing), 403 (not the owner), 404, or 409 (less than 24 hours ahead, or it is no longer
     * scheduled).
     */
    @PatchMapping("/{id:\\d+}/reschedule")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FinalDemonstrationResource.class)))
    public ResponseEntity<?> reschedule(@PathVariable int id,
                                        @RequestBody(required = false) RescheduleFinalDemonstrationResource resource,
                                        @AuthenticationPrincipal User actor) {
        var command = new RescheduleFinalDemonstrationCommand(id, actor.getId(),
                resource == null ? null : resource.scheduledAt());
        var result = commandService.handle(command);
        return AssessmentPeerReviewActionResultAssembler.toResponse(result, demonstration -> ResponseEntity
                .ok(AssessmentPeerReviewResourceAssemblers.toResource(demonstration)));
    }

    /**
     * The student cancels a scheduled demonstration, so the path can schedule another one. 200; 403 (not the
     * owner), 404, or 409 (it is no longer scheduled).
     */
    @PatchMapping("/{id:\\d+}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FinalDemonstrationResource.class)))
    public ResponseEntity<?> cancel(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new CancelFinalDemonstrationCommand(id, actor.getId()));
        return AssessmentPeerReviewActionResultAssembler.toResponse(result, demonstration -> ResponseEntity
                .ok(AssessmentPeerReviewResourceAssemblers.toResource(demonstration)));
    }

    /**
     * The student uploads the video of the demonstration as multipart/form-data, in the part "file" (MP4, WebM or
     * QuickTime, up to 25 MB, recorded with camera and microphone). It can be replaced until the verifier reviews it.
     * 200 with the status VideoSubmitted; 400 (no file), 403 (not the owner), 404, 409 (cancelled, approved or
     * rejected), 413 (over 25 MB), 415 (not an MP4, WebM or QuickTime video, or its type does not match the declared
     * one), or 502 (the storage failed).
     */
    @PostMapping(value = "/{id:\\d+}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FinalDemonstrationResource.class)))
    public ResponseEntity<?> uploadVideo(@PathVariable int id,
                                         @RequestParam(value = "file", required = false) MultipartFile file,
                                         @AuthenticationPrincipal User actor) {
        byte[] content = null;
        if (file != null) {
            try {
                content = file.getBytes();
            } catch (IOException exception) {
                content = null;
            }
        }
        var command = new SubmitDemonstrationVideoCommand(id, actor.getId(), content,
                file == null ? null : file.getContentType());
        var result = evaluationService.handle(command);
        return AssessmentPeerReviewActionResultAssembler.toResponse(result, demonstration -> ResponseEntity
                .ok(AssessmentPeerReviewResourceAssemblers.toResource(demonstration)));
    }

    /**
     * A temporary link (15 minutes) to the video of the demonstration. Only the student and the verifier it is
     * assigned to can get it. 200, 403, or 404 (the demonstration does not exist or has no video yet).
     */
    @GetMapping("/{id:\\d+}/video")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = DemonstrationVideoResource.class)))
    public ResponseEntity<?> getVideo(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var found = queryService.handle(new GetFinalDemonstrationByIdQuery(id));
        if (found.isEmpty()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource,
                    AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
        }
        var demonstration = found.get();
        if (!demonstration.isOwnedBy(actor.getId()) && !demonstration.isAssignedTo(actor.getId())) {
            return AssessmentPeerReviewErrorResponses.of(messageSource,
                    AssessmentPeerReviewError.NOT_DEMONSTRATION_PARTICIPANT);
        }

        var link = queryService.handle(new GetDemonstrationVideoQuery(id));
        if (link.isEmpty()) {
            return AssessmentPeerReviewErrorResponses.of(messageSource, AssessmentPeerReviewError.VIDEO_NOT_FOUND);
        }
        return ResponseEntity.ok(new DemonstrationVideoResource(link.get().url(), link.get().contentType(),
                link.get().expiresAt()));
    }

    /**
     * The assigned verifier scores the video with the rubric (0 to 100): 70 or more approves it, less rejects it and
     * needs a feedback. The student receives a push notification (FinalDemonstrationApproved or
     * FinalDemonstrationRejected). 200; 400 (score or feedback), 403 (not the assigned verifier), 404, or 409 (the
     * video was not submitted yet, or it was already reviewed).
     */
    @PatchMapping("/{id:\\d+}/review")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FinalDemonstrationResource.class)))
    public ResponseEntity<?> review(@PathVariable int id,
                                    @RequestBody(required = false) ReviewFinalDemonstrationResource resource,
                                    @AuthenticationPrincipal User actor) {
        var command = new ReviewFinalDemonstrationCommand(id, actor.getId(),
                resource == null ? null : resource.score(), resource == null ? null : resource.feedback());
        var result = evaluationService.handle(command);
        return AssessmentPeerReviewActionResultAssembler.toResponse(result, demonstration -> ResponseEntity
                .ok(AssessmentPeerReviewResourceAssemblers.toResource(demonstration)));
    }

    private static DemonstrationModality parseModality(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DemonstrationModality.fromValue(value.strip());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
