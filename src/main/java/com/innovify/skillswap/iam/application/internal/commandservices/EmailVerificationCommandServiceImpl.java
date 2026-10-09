package com.innovify.skillswap.iam.application.internal.commandservices;

import com.innovify.skillswap.iam.application.commandservices.EmailVerificationCommandService;
import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.ResendVerificationEmailCommand;
import com.innovify.skillswap.iam.domain.model.commands.VerifyEmailCommand;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Email verification command service. Like {@link UserCommandServiceImpl}, it is not {@code @Transactional}: each
 * save commits on its own, so the verification email is requested only once the new token is stored.
 */
@Service
public class EmailVerificationCommandServiceImpl implements EmailVerificationCommandService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationCommandServiceImpl.class);

    /** Base64url of 32 bytes is 43 characters; anything far longer is not one of our tokens. */
    private static final int MAX_TOKEN_LENGTH = 128;

    private final UserRepository userRepository;
    private final EmailVerificationIssuer issuer;
    private final MessageSource messageSource;

    public EmailVerificationCommandServiceImpl(UserRepository userRepository, EmailVerificationIssuer issuer,
                                               MessageSource messageSource) {
        this.userRepository = userRepository;
        this.issuer = issuer;
        this.messageSource = messageSource;
    }

    @Override
    public Result<User> handle(VerifyEmailCommand command) {
        String token = command.token() == null ? "" : command.token().strip();
        if (token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return failure(IamError.INVALID_VERIFICATION_TOKEN);
        }

        // An unknown token, one already used and one replaced by a newer email look the same.
        Optional<User> found = userRepository.findByVerificationTokenHash(EmailVerificationIssuer.hash(token));
        if (found.isEmpty()) {
            return failure(IamError.INVALID_VERIFICATION_TOKEN);
        }

        User user = found.get();
        if (user.isVerificationTokenExpired(issuer.now())) {
            return failure(IamError.VERIFICATION_TOKEN_EXPIRED);
        }

        user.verify();
        try {
            return Result.success(userRepository.save(user));
        } catch (DataAccessException exception) {
            log.error("The verification of the user {} could not be saved", user.getId(), exception);
            return failure(IamError.DATABASE_ERROR);
        } catch (RuntimeException exception) {
            log.error("Unexpected error while verifying the user {}", user.getId(), exception);
            return failure(IamError.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Result<Void> handle(ResendVerificationEmailCommand command) {
        if (!Email.isValid(command.email())) {
            return Result.success();
        }

        Optional<User> found = userRepository.findByEmail(new Email(command.email()));
        if (found.isPresent() && !found.get().isVerified()) {
            try {
                issuer.reissue(found.get());
            } catch (RuntimeException exception) {
                // Still a success: a different answer would tell that the email is registered.
                log.error("A new verification email for the user {} could not be issued", found.get().getId(),
                        exception);
            }
        }
        return Result.success();
    }

    private <T> Result<T> failure(IamError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }
}
