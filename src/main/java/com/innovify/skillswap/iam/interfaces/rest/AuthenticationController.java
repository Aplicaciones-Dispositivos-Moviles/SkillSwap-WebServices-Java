package com.innovify.skillswap.iam.interfaces.rest;

import com.innovify.skillswap.iam.application.commandservices.EmailVerificationCommandService;
import com.innovify.skillswap.iam.domain.model.commands.ResendVerificationEmailCommand;
import com.innovify.skillswap.iam.domain.model.commands.VerifyEmailCommand;
import com.innovify.skillswap.iam.interfaces.rest.resources.MessageResource;
import com.innovify.skillswap.iam.interfaces.rest.resources.ResendVerificationResource;
import com.innovify.skillswap.iam.interfaces.rest.resources.SignInResource;
import com.innovify.skillswap.iam.interfaces.rest.resources.VerifyEmailResource;
import com.innovify.skillswap.iam.interfaces.rest.transform.EmailVerificationPageAssembler;
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
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up, sign-in and email verification. Every endpoint is anonymous (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/authentication")
public class AuthenticationController {

    static final String VERIFICATION_REQUESTED_MESSAGE = "VerificationEmailRequested";

    private final UserCommandService userCommandService;
    private final EmailVerificationCommandService emailVerificationCommandService;
    private final MessageSource messageSource;

    public AuthenticationController(UserCommandService userCommandService,
                                    EmailVerificationCommandService emailVerificationCommandService,
                                    MessageSource messageSource) {
        this.userCommandService = userCommandService;
        this.emailVerificationCommandService = emailVerificationCommandService;
        this.messageSource = messageSource;
    }

    /**
     * Registers a new Student account with an institutional (.edu.pe) email and sends it a verification email.
     * 201 with the profile (isVerified false), 400 for an invalid username, email or password, 409 when the
     * username or email is already taken.
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

    /**
     * Authenticates with username and password and returns a JWT. 200, 401 for invalid credentials, or 403
     * EmailNotVerified when the password is right but the email was not verified yet (a new verification email
     * is sent, at most once per cooldown).
     */
    @PostMapping("/sign-in")
    public ResponseEntity<?> signIn(@Valid @RequestBody SignInResource resource) {
        var command = SignInCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = userCommandService.handle(command);

        return IamActionResultAssembler.toResponse(result, authenticated -> ResponseEntity.ok(
                AuthenticatedUserResourceFromEntityAssembler.toResourceFromEntity(
                        authenticated.user(), authenticated.token())));
    }

    /**
     * Verifies the institutional email with the token of the link. 200 with the profile, 400
     * InvalidVerificationToken for an unknown, used or replaced token, 410 VerificationTokenExpired.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailResource resource) {
        var result = emailVerificationCommandService.handle(new VerifyEmailCommand(resource.token()));

        return IamActionResultAssembler.toResponse(result,
                user -> ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(user)));
    }

    /** The link of the email, opened in a browser: verifies like the POST and answers a small HTML page. */
    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmailFromLink(@RequestParam(name = "token", required = false) String token) {
        return EmailVerificationPageAssembler.toPage(
                emailVerificationCommandService.handle(new VerifyEmailCommand(token)));
    }

    /**
     * Sends a new verification email when the account exists and is not verified, at most once per cooldown.
     * Always 202 with the same message, so it does not reveal which emails are registered.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationResource resource) {
        emailVerificationCommandService.handle(new ResendVerificationEmailCommand(resource.email()));

        String message = messageSource.getMessage(VERIFICATION_REQUESTED_MESSAGE, null,
                VERIFICATION_REQUESTED_MESSAGE, LocaleContextHolder.getLocale());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResource(message));
    }
}
