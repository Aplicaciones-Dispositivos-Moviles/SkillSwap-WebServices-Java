package com.innovify.skillswap.iam.interfaces.rest;

import com.innovify.skillswap.iam.interfaces.rest.resources.UpdateUserBioResource;
import com.innovify.skillswap.iam.interfaces.rest.transform.IamActionResultAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.PublicUserResourceFromEntityAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import com.innovify.skillswap.iam.application.commandservices.UserCommandService;
import com.innovify.skillswap.iam.application.queryservices.UserQueryService;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByIdQuery;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * A user by id. The account owner and Coordinators receive the full profile (UserResource); any other
     * authenticated user receives the public profile (PublicUserResource), which excludes the email.
     */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<?> getUserById(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var user = userQueryService.handle(new GetUserByIdQuery(id));
        if (user.isEmpty()) {
            String message = messageSource.getMessage("UserNotFound", null, "UserNotFound",
                    LocaleContextHolder.getLocale());
            return IamActionResultAssembler.toUserNotFound(message);
        }

        boolean canSeePrivateData = Objects.equals(user.get().getId(), actor.getId())
                || actor.getRole() == UserRole.COORDINATOR;

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
}
