package com.innovify.skillswap.recognitionincentives.infrastructure.config;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.iam.application.acl.IamContextFacade;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.SkillCatalogContextFacade;
import com.innovify.skillswap.recognitionincentives.application.acl.RecognitionContextFacade;
import com.innovify.skillswap.recognitionincentives.application.acl.RecognitionContextFacadeImpl;
import com.innovify.skillswap.recognitionincentives.application.commandservices.ShareableCredentialCommandService;
import com.innovify.skillswap.recognitionincentives.application.commandservices.WalletCommandService;
import com.innovify.skillswap.recognitionincentives.application.eventhandlers.CreateWalletEventHandler;
import com.innovify.skillswap.recognitionincentives.application.eventhandlers.CreditVerifierEventHandler;
import com.innovify.skillswap.recognitionincentives.application.internal.CredentialViews;
import com.innovify.skillswap.recognitionincentives.application.internal.commandservices.ShareableCredentialCommandServiceImpl;
import com.innovify.skillswap.recognitionincentives.application.internal.commandservices.WalletCommandServiceImpl;
import com.innovify.skillswap.recognitionincentives.application.internal.queryservices.ShareableCredentialQueryServiceImpl;
import com.innovify.skillswap.recognitionincentives.application.internal.queryservices.WalletQueryServiceImpl;
import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialQueryService;
import com.innovify.skillswap.recognitionincentives.application.queryservices.WalletQueryService;
import com.innovify.skillswap.recognitionincentives.domain.repositories.CreditTransactionRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.ShareableCredentialRepository;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.recognitionincentives.domain.services.CredentialTokenGenerator;
import com.innovify.skillswap.recognitionincentives.domain.services.DefaultRedemptionPricing;
import com.innovify.skillswap.recognitionincentives.domain.services.RedemptionPricing;
import com.innovify.skillswap.recognitionincentives.domain.services.SecureRandomCredentialTokenGenerator;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Wiring of the Recognition &amp; Incentives beans that are not annotated themselves. */
@Configuration
public class RecognitionIncentivesConfig {

    @Bean
    public RedemptionPricing redemptionPricing() {
        return new DefaultRedemptionPricing();
    }

    /**
     * The credit commands come from event handlers that run after the publishing transaction committed, so
     * the wallet is changed in a transaction of its own. The template is created here and is not a bean, so it
     * does not replace the default {@code TransactionOperations} of Spring Boot used by the other contexts.
     */
    @Bean
    public WalletCommandService walletCommandService(WalletRepository wallets,
                                                     CreditTransactionRepository transactions,
                                                     RedemptionPricing pricing,
                                                     PlatformTransactionManager transactionManager,
                                                     DomainEventPublisher eventPublisher,
                                                     MessageSource messageSource) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new WalletCommandServiceImpl(wallets, transactions, pricing, template, eventPublisher, messageSource);
    }

    @Bean
    public RecognitionContextFacade recognitionContextFacade(WalletRepository wallets,
                                                             CreditTransactionRepository transactions) {
        return new RecognitionContextFacadeImpl(wallets, transactions);
    }

    @Bean
    public WalletQueryService walletQueryService(WalletRepository wallets, CreditTransactionRepository transactions) {
        return new WalletQueryServiceImpl(wallets, transactions);
    }

    @Bean
    public CredentialTokenGenerator credentialTokenGenerator() {
        return new SecureRandomCredentialTokenGenerator();
    }

    @Bean
    public CredentialViews credentialViews(WalletRepository wallets, SkillCatalogContextFacade skillCatalog,
                                           IamContextFacade iam, MessageSource messageSource,
                                           @Value("${app.credentials.base-url}") String baseUrl) {
        return new CredentialViews(wallets, skillCatalog, iam, messageSource, baseUrl);
    }

    @Bean
    public ShareableCredentialCommandService shareableCredentialCommandService(
            ShareableCredentialRepository credentials, LearningPathContextFacade learningPaths,
            CredentialTokenGenerator tokens, CredentialViews views, MessageSource messageSource) {
        return new ShareableCredentialCommandServiceImpl(credentials, learningPaths, tokens, views,
                Clock.systemUTC(), messageSource);
    }

    @Bean
    public ShareableCredentialQueryService shareableCredentialQueryService(
            ShareableCredentialRepository credentials, CredentialViews views) {
        return new ShareableCredentialQueryServiceImpl(credentials, views);
    }

    @Bean
    public DomainEventHandler<UserRegistered> createWalletEventHandler(WalletCommandService commandService) {
        return new CreateWalletEventHandler(commandService);
    }

    @Bean
    public DomainEventHandler<VerificationCaseResolved> creditVerifierEventHandler(
            WalletCommandService commandService) {
        return new CreditVerifierEventHandler(commandService);
    }
}
