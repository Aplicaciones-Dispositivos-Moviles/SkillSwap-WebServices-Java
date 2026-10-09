package com.innovify.skillswap.reputation.infrastructure.config;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.AssessmentAttemptPassed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacadeImpl;
import com.innovify.skillswap.reputation.application.commandservices.ReputationCommandService;
import com.innovify.skillswap.reputation.application.eventhandlers.RecordAutomaticApprovalEventHandler;
import com.innovify.skillswap.reputation.application.eventhandlers.RecordCaseResolutionEventHandler;
import com.innovify.skillswap.reputation.application.internal.commandservices.ReputationCommandServiceImpl;
import com.innovify.skillswap.reputation.application.internal.queryservices.StudentEmployabilityQueryServiceImpl;
import com.innovify.skillswap.reputation.application.internal.queryservices.VerifierReliabilityQueryServiceImpl;
import com.innovify.skillswap.reputation.application.queryservices.StudentEmployabilityQueryService;
import com.innovify.skillswap.reputation.application.queryservices.VerifierReliabilityQueryService;
import com.innovify.skillswap.reputation.domain.repositories.StudentEmployabilityScoreRepository;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.services.DefaultEmployabilityScoreCalculator;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.reputation.domain.services.EmployabilityScoreCalculator;
import com.innovify.skillswap.reputation.domain.services.VerifierReliabilityCalculator;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Wiring of the Reputation beans that are not annotated themselves. */
@Configuration
public class ReputationConfig {

    @Bean
    public VerifierReliabilityCalculator verifierReliabilityCalculator() {
        return new DefaultVerifierReliabilityCalculator();
    }

    @Bean
    public EmployabilityScoreCalculator employabilityScoreCalculator() {
        return new DefaultEmployabilityScoreCalculator();
    }

    /**
     * The commands come from event handlers that run after the publishing transaction committed, so they
     * write in a transaction of their own. The template is created here and is not a bean, so it does not
     * replace the default {@code TransactionOperations} of Spring Boot used by the other contexts.
     */
    @Bean
    public ReputationCommandService reputationCommandService(
            VerifierReliabilityRepository reliabilityRepository,
            StudentEmployabilityScoreRepository employabilityRepository,
            VerifierReliabilityCalculator reliabilityCalculator,
            EmployabilityScoreCalculator employabilityCalculator,
            VerifierProfileContextFacade verifierProfileFacade,
            PlatformTransactionManager transactionManager,
            MessageSource messageSource) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new ReputationCommandServiceImpl(reliabilityRepository, employabilityRepository,
                reliabilityCalculator, employabilityCalculator, verifierProfileFacade, template, messageSource);
    }

    @Bean
    public StudentEmployabilityQueryService studentEmployabilityQueryService(
            StudentEmployabilityScoreRepository repository) {
        return new StudentEmployabilityQueryServiceImpl(repository);
    }

    @Bean
    public VerifierReliabilityQueryService verifierReliabilityQueryService(
            VerifierReliabilityRepository repository) {
        return new VerifierReliabilityQueryServiceImpl(repository);
    }

    @Bean
    public ReputationContextFacade reputationContextFacade(VerifierReliabilityRepository repository) {
        return new ReputationContextFacadeImpl(repository);
    }

    @Bean
    public DomainEventHandler<AssessmentAttemptPassed> recordAutomaticApprovalEventHandler(
            ReputationCommandService commandService) {
        return new RecordAutomaticApprovalEventHandler(commandService);
    }

    @Bean
    public DomainEventHandler<VerificationCaseResolved> recordCaseResolutionEventHandler(
            ReputationCommandService commandService) {
        return new RecordCaseResolutionEventHandler(commandService);
    }
}
