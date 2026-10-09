package com.innovify.skillswap.iam.application.internal.commandservices;

import com.innovify.skillswap.iam.application.commandservices.UserCommandService;
import com.innovify.skillswap.iam.application.internal.outboundservices.AuthenticatedUser;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.RegisterDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.RemoveDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignUpCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateInterestProfileCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserFullNameCommand;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.iam.domain.services.EmailDomainValidator;
import com.innovify.skillswap.iam.domain.services.PasswordHasher;
import com.innovify.skillswap.learningpathengine.application.acl.SkillCatalogContextFacade;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

/**
 * User command service.
 *
 * <p>It is deliberately not {@code @Transactional}: each {@link UserRepository#save} commits on its own, so a
 * persistence failure is caught here and returned as a {@link Result}, and the domain events are published
 * once the change is really saved.
 */
@Service
public class UserCommandServiceImpl implements UserCommandService {

    private static final Logger log = LoggerFactory.getLogger(UserCommandServiceImpl.class);

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_BYTES = 72; // BCrypt ignores anything beyond 72 bytes

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final EmailDomainValidator emailDomainValidator;
    private final TokenGenerator tokenGenerator;
    private final DomainEventPublisher eventPublisher;
    private final EmailVerificationIssuer verificationIssuer;
    private final SkillCatalogContextFacade skillCatalog;
    private final MessageSource messageSource;

    public UserCommandServiceImpl(UserRepository userRepository,
                                  PasswordHasher passwordHasher,
                                  EmailDomainValidator emailDomainValidator,
                                  TokenGenerator tokenGenerator,
                                  DomainEventPublisher eventPublisher,
                                  EmailVerificationIssuer verificationIssuer,
                                  SkillCatalogContextFacade skillCatalog,
                                  MessageSource messageSource) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.emailDomainValidator = emailDomainValidator;
        this.tokenGenerator = tokenGenerator;
        this.eventPublisher = eventPublisher;
        this.verificationIssuer = verificationIssuer;
        this.skillCatalog = skillCatalog;
        this.messageSource = messageSource;
    }

    @Override
    public Result<User> handle(SignUpCommand command) {
        if (!Username.isValid(command.username())) {
            return failure(IamError.INVALID_USERNAME);
        }
        if (!emailDomainValidator.isInstitutionalDomain(command.email())) {
            return failure(IamError.INVALID_INSTITUTIONAL_EMAIL);
        }
        if (!isAcceptablePassword(command.password())) {
            return failure(IamError.WEAK_PASSWORD);
        }
        if (!User.isValidFullName(command.fullName())) {
            return failure(IamError.INVALID_FULL_NAME);
        }

        Username username = new Username(command.username());
        Email email = new Email(command.email());

        if (userRepository.existsByUsername(username)) {
            return failure(IamError.USERNAME_ALREADY_TAKEN);
        }
        if (userRepository.existsByEmail(email)) {
            return failure(IamError.EMAIL_ALREADY_TAKEN);
        }

        User user = new User(username, email, passwordHasher.hashPassword(command.password()), command.role())
                .updateFullName(command.fullName());
        // The account starts unverified, with the token of the verification email saved along with it.
        EmailVerificationIssuer.IssuedToken verification = verificationIssuer.issue(user);
        Result<User> saved = save(user);
        if (saved.isFailure()) {
            return saved;
        }

        // Other bounded contexts (the wallet, later the free plan) react to the new account.
        User created = saved.value();
        eventPublisher.publish(new UserRegistered(created.getId(), created.getRole()));
        verificationIssuer.requestEmail(created, verification);
        return saved;
    }

    @Override
    public Result<AuthenticatedUser> handle(SignInCommand command) {
        // Same error for unknown user and wrong password, so the API does not reveal which usernames exist.
        if (!Username.isValid(command.username()) || command.password() == null) {
            return failure(IamError.INVALID_CREDENTIALS);
        }

        Optional<User> found = userRepository.findByUsername(new Username(command.username()));
        if (found.isEmpty() || !passwordHasher.verifyPassword(command.password(), found.get().getPasswordHash())) {
            return failure(IamError.INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (!user.isVerified()) {
            // Only with the right password: a wrong one keeps answering InvalidCredentials.
            resendVerificationEmail(user);
            return failure(IamError.EMAIL_NOT_VERIFIED);
        }
        return Result.success(new AuthenticatedUser(user, tokenGenerator.generateToken(user)));
    }

    @Override
    public Result<User> handle(UpdateUserBioCommand command) {
        Optional<User> found = userRepository.findById(command.userId());
        if (found.isEmpty()) {
            return failure(IamError.USER_NOT_FOUND);
        }

        User user = found.get();
        if (!Objects.equals(user.getId(), command.actorUserId())) {
            return failure(IamError.NOT_PROFILE_OWNER);
        }

        // A missing bio clears it; the REST layer rejects a null body field before reaching this point.
        String bio = command.bio() == null ? "" : command.bio();
        if (bio.strip().length() > User.MAX_BIO_LENGTH) {
            return failure(IamError.BIO_TOO_LONG);
        }

        user.updateBio(bio);
        // The description is part of the skill vector.
        user.updateSkillVector(skillVectorOf(user.getInterestTopics(), user.getBio()));
        return save(user);
    }

    @Override
    public Result<User> handle(UpdateInterestProfileCommand command) {
        Optional<User> found = userRepository.findById(command.userId());
        if (found.isEmpty()) {
            return failure(IamError.USER_NOT_FOUND);
        }

        User user = found.get();
        if (!Objects.equals(user.getId(), command.actorUserId())) {
            return failure(IamError.NOT_PROFILE_OWNER);
        }

        IamError topicsError = validateInterestTopics(command.topics());
        if (topicsError != null) {
            return failure(topicsError);
        }
        if (command.description() != null && command.description().strip().length() > User.MAX_BIO_LENGTH) {
            return failure(IamError.BIO_TOO_LONG);
        }

        user.replaceInterestTopics(command.topics());
        if (command.description() != null) {
            user.updateBio(command.description());
        }
        user.updateSkillVector(skillVectorOf(user.getInterestTopics(), user.getBio()));
        return save(user);
    }

    private static IamError validateInterestTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return IamError.INTEREST_TOPICS_REQUIRED;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String topic : topics) {
            if (topic == null || topic.isBlank()
                    || topic.strip().replaceAll("\\s+", " ").length() > User.MAX_INTEREST_TOPIC_LENGTH) {
                return IamError.INVALID_INTEREST_TOPIC;
            }
            unique.add(topic.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
        }
        return unique.size() > User.MAX_INTEREST_TOPICS ? IamError.TOO_MANY_INTEREST_TOPICS : null;
    }

    /** The catalog skills each topic and the description refer to, in that order and without duplicates. */
    private List<String> skillVectorOf(List<String> topics, String description) {
        Set<String> tags = new LinkedHashSet<>();
        for (String topic : topics) {
            tags.addAll(skillCatalog.matchSkillTags(topic));
        }
        tags.addAll(skillCatalog.matchSkillTags(description));
        return List.copyOf(tags);
    }

    @Override
    public Result<User> handle(RegisterDeviceTokenCommand command) {
        if (!DeviceToken.isValid(command.token())) {
            return failure(IamError.INVALID_DEVICE_TOKEN);
        }
        Optional<User> found = userRepository.findById(command.userId());
        if (found.isEmpty()) {
            return failure(IamError.USER_NOT_FOUND);
        }

        DeviceToken token = new DeviceToken(command.token());
        // One device, one account: whoever signed in before on this device stops receiving its notifications.
        try {
            for (User previousOwner : userRepository.findByDeviceToken(token)) {
                if (!Objects.equals(previousOwner.getId(), command.userId())) {
                    userRepository.save(previousOwner.removeDeviceToken());
                }
            }
        } catch (RuntimeException exception) {
            log.error("The device token could not be removed from its previous account", exception);
            return failure(exception instanceof DataAccessException
                    ? IamError.DATABASE_ERROR
                    : IamError.INTERNAL_SERVER_ERROR);
        }

        return save(found.get().registerDeviceToken(token.value()));
    }

    @Override
    public Result<User> handle(RemoveDeviceTokenCommand command) {
        Optional<User> found = userRepository.findById(command.userId());
        if (found.isEmpty()) {
            return failure(IamError.USER_NOT_FOUND);
        }
        User user = found.get();
        return user.hasDeviceToken() ? save(user.removeDeviceToken()) : Result.success(user);
    }

    /** A failure to send it again must not hide the reason of the rejected sign-in, so it is only logged. */
    private void resendVerificationEmail(User user) {
        try {
            verificationIssuer.reissue(user);
        } catch (RuntimeException exception) {
            log.error("A new verification email for the user {} could not be issued", user.getId(), exception);
        }
    }

    @Override
    public Result<User> handle(UpdateUserFullNameCommand command) {
        Optional<User> found = userRepository.findById(command.userId());
        if (found.isEmpty()) {
            return failure(IamError.USER_NOT_FOUND);
        }

        User user = found.get();
        if (!Objects.equals(user.getId(), command.actorUserId())) {
            return failure(IamError.NOT_PROFILE_OWNER);
        }
        if (!User.isValidFullName(command.fullName())) {
            return failure(IamError.INVALID_FULL_NAME);
        }

        user.updateFullName(command.fullName());
        return save(user);
    }

    private Result<User> save(User user) {
        try {
            return Result.success(userRepository.save(user));
        } catch (DataAccessException exception) {
            log.error("The user could not be saved", exception);
            return failure(IamError.DATABASE_ERROR);
        } catch (RuntimeException exception) {
            log.error("Unexpected error while saving the user", exception);
            return failure(IamError.INTERNAL_SERVER_ERROR);
        }
    }

    private static boolean isAcceptablePassword(String password) {
        return password != null
                && password.length() >= MIN_PASSWORD_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    private <T> Result<T> failure(IamError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }
}
