package com.innovify.skillswap.iam.interfaces.rest;

import com.innovify.skillswap.iam.domain.model.commands.RegisterDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.RemoveDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateInterestProfileCommand;
import com.innovify.skillswap.iam.interfaces.rest.resources.RegisterDeviceTokenResource;
import com.innovify.skillswap.iam.interfaces.rest.resources.UpdateInterestProfileResource;
import com.innovify.skillswap.iam.interfaces.rest.resources.UpdateUserBioResource;
import com.innovify.skillswap.iam.interfaces.rest.resources.UpdateUserFullNameResource;
import com.innovify.skillswap.iam.interfaces.rest.transform.IamActionResultAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.PublicUserResourceFromEntityAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import com.innovify.skillswap.iam.application.commandservices.UserCommandService;
import com.innovify.skillswap.iam.application.queryservices.UserQueryService;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserFullNameCommand;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByIdQuery;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User profiles. Every endpoint needs a valid token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final MessageSource messageSource;

    public UsersController(UserQueryService userQueryService, UserCommandService userCommandService,
                           MessageSource messageSource) {
        this.userQueryService = userQueryService;
        this.userCommandService = userCommandService;
        this.messageSource = messageSource;
    }

    /** The full profile of the authenticated user. */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(actor));
    }

    /**
     * A user by id. The account owner receives the full profile (UserResource); any other authenticated user
     * receives the public profile (PublicUserResource), which excludes the email.
     */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<?> getUserById(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var user = userQueryService.handle(new GetUserByIdQuery(id));
        if (user.isEmpty()) {
            String message = messageSource.getMessage("UserNotFound", null, "UserNotFound",
                    LocaleContextHolder.getLocale());
            return IamActionResultAssembler.toUserNotFound(message);
        }

        boolean canSeePrivateData = Objects.equals(user.get().getId(), actor.getId());

        return ResponseEntity.ok(canSeePrivateData
                ? UserResourceFromEntityAssembler.toResourceFromEntity(user.get())
                : PublicUserResourceFromEntityAssembler.toResourceFromEntity(user.get()));
    }

    /** Updates the authenticated user's profile description. 403 when the profile is someone else's. */
    @PatchMapping("/{id:\\d+}/bio")
    public ResponseEntity<?> updateUserBio(@PathVariable int id, @Valid @RequestBody UpdateUserBioResource resource,
                                           @AuthenticationPrincipal User actor) {
        var command = new UpdateUserBioCommand(id, resource.bio(), actor.getId());
        var result = userCommandService.handle(command);

        return IamActionResultAssembler.toResponse(result,
                user -> ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(user)));
    }

    /**
     * Registers or replaces the interest topics of the authenticated user's profile, and optionally its
     * description; the skill vector is recalculated from both (US04). 200 with the profile, 400 for invalid
     * topics or a too long description, 403 when the profile is someone else's.
     */
    @PutMapping("/{id:\\d+}/interests")
    public ResponseEntity<?> updateInterestProfile(@PathVariable int id,
                                                   @Valid @RequestBody UpdateInterestProfileResource resource,
                                                   @AuthenticationPrincipal User actor) {
        var command = new UpdateInterestProfileCommand(id, resource.topics(), resource.description(), actor.getId());
        var result = userCommandService.handle(command);

        return IamActionResultAssembler.toResponse(result,
                user -> ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(user)));
    }

    /**
     * Updates the real name of the authenticated user, the one the holder of their certificates is compared with
     * (a certificate issued to someone else is escalated to a verifier). 200; 400 (longer than 150 characters),
     * 403 when the profile is someone else's, or 404.
     */
    @PatchMapping("/{id:\\d+}/full-name")
    public ResponseEntity<?> updateUserFullName(@PathVariable int id,
                                                @RequestBody(required = false) UpdateUserFullNameResource resource,
                                                @AuthenticationPrincipal User actor) {
        String fullName = resource == null ? null : resource.fullName();
        var result = userCommandService.handle(new UpdateUserFullNameCommand(id, fullName, actor.getId()));

        return IamActionResultAssembler.toResponse(result,
                user -> ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(user)));
    }

    /**
     * Registers (or replaces) the Firebase Cloud Messaging token of the device of the authenticated user, once the
     * student grants the notification permission. 204, or 400 InvalidDeviceToken.
     */
    @PutMapping("/me/device-token")
    public ResponseEntity<?> registerDeviceToken(@Valid @RequestBody RegisterDeviceTokenResource resource,
                                                 @AuthenticationPrincipal User actor) {
        var result = userCommandService.handle(new RegisterDeviceTokenCommand(actor.getId(), resource.token()));

        return IamActionResultAssembler.toResponse(result, user -> ResponseEntity.noContent().build());
    }

    /**
     * Forgets the device token of the authenticated user (notification permission denied or sign-out): no push
     * notification is sent until a new one is registered. 204, also when there was none.
     */
    @DeleteMapping("/me/device-token")
    public ResponseEntity<?> removeDeviceToken(@AuthenticationPrincipal User actor) {
        var result = userCommandService.handle(new RemoveDeviceTokenCommand(actor.getId()));

        return IamActionResultAssembler.toResponse(result, user -> ResponseEntity.noContent().build());
    }
}
