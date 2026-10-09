package com.innovify.skillswap.iam.interfaces.rest;

import com.innovify.skillswap.iam.interfaces.rest.resources.SignInResource;
import com.innovify.skillswap.iam.interfaces.rest.resources.SignUpResource;
import com.innovify.skillswap.iam.interfaces.rest.transform.AuthenticatedUserResourceFromEntityAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.IamActionResultAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.SignInCommandFromResourceAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import com.innovify.skillswap.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import com.innovify.skillswap.iam.application.commandservices.UserCommandService;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up and sign-in. Both endpoints are anonymous (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/authentication")
public class AuthenticationController {

    private final UserCommandService userCommandService;

    public AuthenticationController(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    /**
     * Registers a new Student account with an institutional (.edu.pe) email. 201 with the profile, 400 for an
     * invalid username, email or password, 409 when the username or email is already taken.
     */
    @PostMapping("/sign-up")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpResource resource) {
        // Self-registration can only create Student accounts: the role is never taken from the request, so a
        // client cannot elevate its own role. Every account is a Student.
        var command = SignUpCommandFromResourceAssembler.toCommandFromResource(resource, UserRole.STUDENT);
        var result = userCommandService.handle(command);

        return IamActionResultAssembler.toResponse(result, user -> ResponseEntity
                .created(URI.create("/api/v1/users/" + user.getId()))
                .body(UserResourceFromEntityAssembler.toResourceFromEntity(user)));
    }

    /** Authenticates with username and password and returns a JWT. 200, or 401 for invalid credentials. */
    @PostMapping("/sign-in")
    public ResponseEntity<?> signIn(@Valid @RequestBody SignInResource resource) {
        var command = SignInCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = userCommandService.handle(command);

        return IamActionResultAssembler.toResponse(result, authenticated -> ResponseEntity.ok(
                AuthenticatedUserResourceFromEntityAssembler.toResourceFromEntity(
                        authenticated.user(), authenticated.token())));
    }
}
