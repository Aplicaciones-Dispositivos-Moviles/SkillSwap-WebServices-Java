package com.innovify.skillswap.iam.application.internal.commandservices;

import com.innovify.skillswap.iam.application.commandservices.UserCommandService;
import com.innovify.skillswap.iam.application.internal.outboundservices.AuthenticatedUser;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignUpCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.iam.domain.services.EmailDomainValidator;
import com.innovify.skillswap.iam.domain.services.PasswordHasher;
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
import java.util.Objects;
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
    private final MessageSource messageSource;

    public UserCommandServiceImpl(UserRepository userRepository,
                                  PasswordHasher passwordHasher,
                                  EmailDomainValidator emailDomainValidator,
                                  TokenGenerator tokenGenerator,
                                  DomainEventPublisher eventPublisher,
                                  MessageSource messageSource) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.emailDomainValidator = emailDomainValidator;
        this.tokenGenerator = tokenGenerator;
        this.eventPublisher = eventPublisher;
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

        Username username = new Username(command.username());
        Email email = new Email(command.email());

        if (userRepository.existsByUsername(username)) {
            return failure(IamError.USERNAME_ALREADY_TAKEN);
        }
        if (userRepository.existsByEmail(email)) {
            return failure(IamError.EMAIL_ALREADY_TAKEN);
        }

        User user = new User(username, email, passwordHasher.hashPassword(command.password()), command.role());
        Result<User> saved = save(user);
        if (saved.isFailure()) {
            return saved;
        }

        // Other bounded contexts (the wallet, later the free plan) react to the new account.
        User created = saved.value();
        eventPublisher.publish(new UserRegistered(created.getId(), created.getRole()));
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
