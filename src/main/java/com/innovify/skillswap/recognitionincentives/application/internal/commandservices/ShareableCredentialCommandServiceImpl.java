package com.innovify.skillswap.recognitionincentives.application.internal.commandservices;

import com.innovify.skillswap.recognitionincentives.application.commandservices.IssuedCredential;
import com.innovify.skillswap.recognitionincentives.application.commandservices.ShareableCredentialCommandService;
import com.innovify.skillswap.recognitionincentives.application.internal.CredentialViews;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateShareableCredentialCommand;
import com.innovify.skillswap.recognitionincentives.domain.repositories.ShareableCredentialRepository;
import com.innovify.skillswap.recognitionincentives.domain.services.CredentialTokenGenerator;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Shareable credential command service. Not {@code @Transactional}: it saves a single row. Only a skill the student
 * completed can be shared, and asking again returns the link they already have; two requests at the same time end
 * with the same link thanks to the unique (owner, skill) index.
 */
public class ShareableCredentialCommandServiceImpl implements ShareableCredentialCommandService {

    private static final Logger log = LoggerFactory.getLogger(ShareableCredentialCommandServiceImpl.class);

    private final ShareableCredentialRepository credentials;
    private final LearningPathContextFacade learningPaths;
    private final CredentialTokenGenerator tokens;
    private final CredentialViews views;
    private final Clock clock;
    private final RecognitionIncentivesFailures failures;

    public ShareableCredentialCommandServiceImpl(ShareableCredentialRepository credentials,
                                                 LearningPathContextFacade learningPaths,
                                                 CredentialTokenGenerator tokens, CredentialViews views,
                                                 Clock clock, MessageSource messageSource) {
        this.credentials = credentials;
        this.learningPaths = learningPaths;
        this.tokens = tokens;
        this.views = views;
        this.clock = clock;
        this.failures = new RecognitionIncentivesFailures(messageSource);
    }

    @Override
    public Result<IssuedCredential> handle(CreateShareableCredentialCommand command) {
        try {
            String skillTag = command.skillTag() == null ? "" : command.skillTag().strip();
            if (skillTag.isEmpty() || skillTag.length() > ShareableCredential.MAX_SKILL_TAG_LENGTH) {
                return failures.failure(RecognitionIncentivesError.INVALID_SKILL_TAG);
            }

            Optional<ShareableCredential> existing = credentials.findByOwnerIdAndSkillTag(command.userId(), skillTag);
            if (existing.isPresent()) {
                return Result.success(new IssuedCredential(views.toView(existing.get()), false));
            }
            if (!learningPaths.hasCompletedSkill(command.userId(), skillTag)) {
                return failures.failure(RecognitionIncentivesError.SKILL_NOT_VERIFIED);
            }

            try {
                ShareableCredential saved = credentials.save(
                        new ShareableCredential(command.userId(), skillTag, tokens.generate(), clock.instant()));
                return Result.success(new IssuedCredential(views.toView(saved), true));
            } catch (DataIntegrityViolationException conflict) {
                // Another request created it first: that one is the link of the skill.
                Optional<ShareableCredential> winner = credentials.findByOwnerIdAndSkillTag(command.userId(), skillTag);
                if (winner.isPresent()) {
                    return Result.success(new IssuedCredential(views.toView(winner.get()), false));
                }
                throw conflict;
            }
        } catch (RuntimeException exception) {
            log.error("Could not create the credential of the user {}", command.userId(), exception);
            return failures.failure(RecognitionIncentivesFailures.toError(exception));
        }
    }
}
