package com.innovify.skillswap.reputation.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.reputation.application.queryservices.PlatformMetricsQueryService;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.queries.GetDemandMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetDifficultyMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierMetricsQuery;
import com.innovify.skillswap.reputation.interfaces.rest.transform.PlatformMetricsResourceAssemblers;
import com.innovify.skillswap.reputation.interfaces.rest.transform.ReputationActionResultAssembler;
import com.innovify.skillswap.shared.application.Result;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform metrics for the Verificadores senior: what students ask for, where they fail and who verifies. */
@RestController
@RequestMapping("/api/v1/metrics")
@PreAuthorize("hasRole('STUDENT')")
public class MetricsController {

    private final PlatformMetricsQueryService queryService;

    public MetricsController(PlatformMetricsQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * The skills most requested and the certifications most uploaded. from and to are yyyy-MM-dd (inclusive; last 30
     * days by default, at most 366), limit goes from 1 to 50 (10 by default). 200, 400 or 403 (not a senior verifier).
     */
    @GetMapping("/demand")
    public ResponseEntity<?> demand(@RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) Integer limit,
                                    @AuthenticationPrincipal User actor) {
        return respond(queryService.handle(new GetDemandMetricsQuery(actor.getId(), from, to, limit)),
                PlatformMetricsResourceAssemblers::toResource);
    }

    /** The skills with the highest failure rate and the weakest sub-topics. Same parameters as the demand. */
    @GetMapping("/difficulty")
    public ResponseEntity<?> difficulty(@RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) Integer limit,
                                        @AuthenticationPrincipal User actor) {
        return respond(queryService.handle(new GetDifficultyMetricsQuery(actor.getId(), from, to, limit)),
                PlatformMetricsResourceAssemblers::toResource);
    }

    /** The verifiers ordered by reliability; order is asc or desc (default). 200, 400 or 403. */
    @GetMapping("/verifiers")
    public ResponseEntity<?> verifiers(@RequestParam(required = false) String order,
                                       @AuthenticationPrincipal User actor) {
        return respond(queryService.handle(new GetVerifierMetricsQuery(actor.getId(), order)),
                PlatformMetricsResourceAssemblers::toResource);
    }

    private static <T, R> ResponseEntity<?> respond(Result<T> result, Function<T, R> assembler) {
        if (result.isFailure()) {
            return ReputationActionResultAssembler.toError((ReputationError) result.error(), result.message());
        }
        return ResponseEntity.ok(assembler.apply(result.value()));
    }
}
