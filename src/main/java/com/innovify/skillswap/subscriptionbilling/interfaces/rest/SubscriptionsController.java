package com.innovify.skillswap.subscriptionbilling.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.SubscriptionCommandService;
import com.innovify.skillswap.subscriptionbilling.application.queryservices.SubscriptionQueryService;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CancelSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetCurrentSubscriptionByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.domain.model.queries.GetPlanLimitsByStudentIdQuery;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.CreateSubscriptionResource;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.StudentPlanResource;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.SubscriptionResource;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform.CreateSubscriptionCommandFromResourceAssembler;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform.SubscriptionBillingActionResultAssembler;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform.SubscriptionResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The monthly subscription of a student, bought in the app with Google Play Billing through RevenueCat. The
 * backend never trusts the client about a purchase: it asks RevenueCat for the state of the authenticated student.
 * Every endpoint needs a valid token and acts on the caller's own subscription. The endpoints answer
 * {@code ResponseEntity<?>} (the resource or a problem), so the success body is declared for the documentation.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionsController {

    private final SubscriptionCommandService commandService;
    private final SubscriptionQueryService queryService;
    private final MessageSource messageSource;

    public SubscriptionsController(SubscriptionCommandService commandService, SubscriptionQueryService queryService,
                                   MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /**
     * Called by the app right after a purchase. The entitlement of the authenticated student is verified with
     * RevenueCat (the student id is the RevenueCat app user id) and the subscription is activated with the period
     * RevenueCat reports; when the webhook already activated it, it is returned up to date. 201 with the
     * subscription; 400 (product id), 422 (RevenueCat reports no active purchase) or 503 (RevenueCat did not
     * answer; try again).
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "201",
            content = @Content(schema = @Schema(implementation = SubscriptionResource.class)))
    public ResponseEntity<?> create(@RequestBody(required = false) CreateSubscriptionResource resource,
                                    @AuthenticationPrincipal User actor) {
        var command = CreateSubscriptionCommandFromResourceAssembler.toCommandFromResource(resource, actor.getId());
        var result = commandService.handle(command);
        return SubscriptionBillingActionResultAssembler.toResponse(result,
                subscription -> ResponseEntity.created(URI.create("/api/v1/subscriptions/" + actor.getId()))
                        .body(SubscriptionResourceFromEntityAssembler.toResourceFromEntity(subscription)));
    }

    /**
     * The plan the student is on right now (Free or Premium), its limits and the subscription that has not
     * expired, if any. Only the student can read it. 200 or 403.
     */
    @GetMapping("/{studentId:\\d+}")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = StudentPlanResource.class)))
    public ResponseEntity<?> getByStudentId(@PathVariable int studentId, @AuthenticationPrincipal User actor) {
        if (actor.getId() != studentId) {
            return SubscriptionBillingErrorResponses.of(messageSource, SubscriptionBillingError.NOT_SUBSCRIPTION_OWNER);
        }
        var limits = queryService.handle(new GetPlanLimitsByStudentIdQuery(studentId));
        var current = queryService.handle(new GetCurrentSubscriptionByStudentIdQuery(studentId));
        return ResponseEntity.ok(SubscriptionResourceFromEntityAssembler.toResource(limits, current.orElse(null)));
    }

    /**
     * Cancels the renewals in Google Play (through RevenueCat). The plan is kept until the end of the period
     * already paid (currentPeriodEnd); cancelling twice changes nothing. 200; 403, 404, 409 (already expired) or
     * 503 (RevenueCat did not answer; nothing changed).
     */
    @PatchMapping("/{id:\\d+}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SubscriptionResource.class)))
    public ResponseEntity<?> cancel(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var result = commandService.handle(new CancelSubscriptionCommand(id, actor.getId()));
        return SubscriptionBillingActionResultAssembler.toResponse(result,
                subscription -> ResponseEntity.ok(SubscriptionResourceFromEntityAssembler.toResourceFromEntity(subscription)));
    }
}
