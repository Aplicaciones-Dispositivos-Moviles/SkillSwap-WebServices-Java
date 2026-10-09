package com.innovify.skillswap.subscriptionbilling.interfaces.rest;

import com.innovify.skillswap.subscriptionbilling.application.commandservices.SubscriptionCommandService;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.WebhookEventOutcome;
import com.innovify.skillswap.subscriptionbilling.application.internal.outboundservices.WebhookAuthorizationVerifier;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ProcessRevenueCatEventCommand;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.RevenueCatWebhookResource;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.WebhookAcknowledgementResource;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform.SubscriptionBillingActionResultAssembler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the webhook notifications of RevenueCat (purchase, renewal, cancellation, expiration...). It needs no
 * token: it is protected by the secret configured in the RevenueCat dashboard as the Authorization header.
 */
@RestController
@RequestMapping("/api/v1/subscriptions/webhooks")
public class RevenueCatWebhookController {

    private final SubscriptionCommandService commandService;
    private final WebhookAuthorizationVerifier authorizationVerifier;
    private final MessageSource messageSource;

    public RevenueCatWebhookController(SubscriptionCommandService commandService,
                                       WebhookAuthorizationVerifier authorizationVerifier,
                                       MessageSource messageSource) {
        this.commandService = commandService;
        this.authorizationVerifier = authorizationVerifier;
        this.messageSource = messageSource;
    }

    /**
     * Only RevenueCat calls it, with the configured secret in the Authorization header (401 otherwise). The state
     * of the student is read again from RevenueCat instead of trusting the payload, and each event id is applied
     * once: a retry answers 200 without changing anything. TEST events from the dashboard are acknowledged and
     * ignored. 200; 400 (not a RevenueCat event), 401 or 503 (RevenueCat did not answer: it retries later).
     */
    @PostMapping("/revenuecat")
    @SecurityRequirements
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = WebhookAcknowledgementResource.class)))
    public ResponseEntity<?> receive(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                     String authorization,
                                     @RequestBody(required = false) RevenueCatWebhookResource resource) {
        if (!authorizationVerifier.isAuthorized(authorization)) {
            return SubscriptionBillingErrorResponses.of(messageSource,
                    SubscriptionBillingError.INVALID_WEBHOOK_AUTHORIZATION);
        }
        if (resource == null || resource.event() == null) {
            return SubscriptionBillingErrorResponses.of(messageSource, SubscriptionBillingError.INVALID_WEBHOOK_EVENT);
        }

        RevenueCatWebhookResource.Event event = resource.event();
        var result = commandService.handle(new ProcessRevenueCatEventCommand(event.id(), event.type(),
                event.appUserId(), event.originalAppUserId(), event.aliases(), event.environment()));
        return SubscriptionBillingActionResultAssembler.toResponse(result,
                outcome -> ResponseEntity.ok(new WebhookAcknowledgementResource(toValue(outcome))));
    }

    private static String toValue(WebhookEventOutcome outcome) {
        String name = outcome.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
