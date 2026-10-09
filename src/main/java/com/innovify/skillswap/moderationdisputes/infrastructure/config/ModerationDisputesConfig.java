package com.innovify.skillswap.moderationdisputes.infrastructure.config;

import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import com.innovify.skillswap.credentialverification.domain.model.events.CertificateFlaggedSuspicious;
import com.innovify.skillswap.moderationdisputes.application.commandservices.DisputeCommandService;
import com.innovify.skillswap.moderationdisputes.application.eventhandlers.EscalateCertificateReviewEventHandler;
import com.innovify.skillswap.moderationdisputes.application.internal.commandservices.DisputeCommandServiceImpl;
import com.innovify.skillswap.moderationdisputes.application.internal.queryservices.DisputeQueryServiceImpl;
import com.innovify.skillswap.moderationdisputes.application.queryservices.DisputeQueryService;
import com.innovify.skillswap.moderationdisputes.domain.repositories.DisputeRepository;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeResolutionValidator;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeReviewerSelector;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Wiring of the Moderation &amp; Disputes beans that are not annotated themselves. */
@Configuration
@EnableConfigurationProperties(ModerationSettings.class)
public class ModerationDisputesConfig {

    @Bean
    public DisputeResolutionValidator disputeResolutionValidator() {
        return new DisputeResolutionValidator();
    }

    @Bean
    public DisputeReviewerSelector disputeReviewerSelector() {
        return new DisputeReviewerSelector();
    }

    /**
     * The escalations come from event handlers that may run after the publishing transaction committed, so the
     * disputes change in a transaction of their own. The template is created here and is not a bean, so it does not
     * replace the default {@code TransactionOperations} of Spring Boot used by the other contexts.
     */
    @Bean
    public DisputeCommandService disputeCommandService(DisputeRepository disputeRepository,
                                                       DisputeResolutionValidator resolutionValidator,
                                                       DisputeReviewerSelector reviewerSelector,
                                                       VerifierProfileContextFacade verifierFacade,
                                                       ReputationContextFacade reputationFacade,
                                                       CredentialContextFacade credentialFacade,
                                                       PlatformTransactionManager transactionManager,
                                                       MessageSource messageSource) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new DisputeCommandServiceImpl(disputeRepository, resolutionValidator, reviewerSelector,
                verifierFacade, reputationFacade, credentialFacade, template, messageSource);
    }

    @Bean
    public DisputeQueryService disputeQueryService(DisputeRepository disputeRepository,
                                                   CredentialContextFacade credentialFacade) {
        return new DisputeQueryServiceImpl(disputeRepository, credentialFacade);
    }

    @Bean
    public DomainEventHandler<CertificateFlaggedSuspicious> escalateCertificateReviewEventHandler(
            DisputeCommandService commandService) {
        return new EscalateCertificateReviewEventHandler(commandService);
    }
}
