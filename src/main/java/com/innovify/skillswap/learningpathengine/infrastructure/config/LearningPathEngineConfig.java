package com.innovify.skillswap.learningpathengine.infrastructure.config;

import com.innovify.skillswap.credentialverification.domain.model.events.CertificateVerified;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.application.eventhandlers.EnforcePlanLimitsEventHandler;
import com.innovify.skillswap.learningpathengine.application.eventhandlers.RecognizeValidatedCertificateEventHandler;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateSkillAffinityScorer;
import com.innovify.skillswap.learningpathengine.application.eventhandlers.GrantAdvancedPathUnlockEventHandler;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import com.innovify.skillswap.learningpathengine.domain.services.LearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.SkillGapAnalyzer;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiClient;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiGoalInterpretationSettings;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiQuestionGenerator;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiSettings;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.GeminiSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.JsonSkillTaxonomy;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.KeywordCertificateSkillAffinityScorer;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.KeywordSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.SkillCatalog;
import com.innovify.skillswap.recognitionincentives.domain.model.events.AdvancedPathUnlockRedeemed;
import com.innovify.skillswap.shared.domain.events.DomainEventHandler;
import com.innovify.skillswap.subscriptionbilling.domain.model.events.SubscriptionExpired;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Wiring of the Learning Path Engine beans that are not annotated themselves. */
@Configuration
@EnableConfigurationProperties({GeminiSettings.class, GeminiGoalInterpretationSettings.class})
public class LearningPathEngineConfig {

    /** Loaded and validated once at startup: an inconsistent catalog stops the application. */
    @Bean
    public SkillCatalog skillCatalog() {
        return SkillCatalog.loadEmbedded();
    }

    @Bean
    public SkillTaxonomy skillTaxonomy(SkillCatalog catalog) {
        return new JsonSkillTaxonomy(catalog);
    }

    /** The single Gemini client of the context: the question generator and the goal interpreter share it. */
    @Bean
    public GeminiClient geminiClient(GeminiSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new GeminiClient(settings, httpClient);
    }

    /**
     * Hybrid interpretation of the goal: Gemini selects skills of the catalog and the keyword matcher answers when
     * Gemini fails, times out or selects no valid skill. Without a key, or with
     * gemini.goal-interpretation.enabled=false, only the keyword matcher is used.
     */
    @Bean
    public SkillTaxonomyMatcher skillTaxonomyMatcher(SkillCatalog catalog, GeminiSettings settings,
                                                     GeminiGoalInterpretationSettings goalSettings,
                                                     GeminiClient geminiClient) {
        return skillTaxonomyMatcherFor(catalog, settings, goalSettings, geminiClient);
    }

    static SkillTaxonomyMatcher skillTaxonomyMatcherFor(SkillCatalog catalog, GeminiSettings settings,
                                                        GeminiGoalInterpretationSettings goalSettings,
                                                        GeminiClient geminiClient) {
        SkillTaxonomyMatcher keywords = new KeywordSkillTaxonomyMatcher(catalog);
        boolean hasKey = settings != null && settings.apiKey() != null && !settings.apiKey().isBlank();
        if (!hasKey || goalSettings == null || !goalSettings.enabled()) {
            return keywords;
        }
        return new GeminiSkillTaxonomyMatcher(geminiClient, catalog, keywords,
                Duration.ofSeconds(goalSettings.timeoutSeconds()));
    }

    /** Compares certificates with skills by the keywords of the catalog (scale and threshold in SkillAffinity). */
    @Bean
    public CertificateSkillAffinityScorer certificateSkillAffinityScorer(SkillCatalog catalog) {
        return new KeywordCertificateSkillAffinityScorer(catalog);
    }

    @Bean
    public SkillGapAnalyzer skillGapAnalyzer(SkillTaxonomy taxonomy) {
        return new DefaultSkillGapAnalyzer(taxonomy);
    }

    @Bean
    public LearningPathBuilder learningPathBuilder(SkillTaxonomy taxonomy) {
        return new DefaultLearningPathBuilder(taxonomy);
    }

    @Bean
    public QuestionGenerationService questionGenerationService(GeminiClient geminiClient) {
        return new GeminiQuestionGenerator(geminiClient);
    }

    /**
     * The expiration of a subscription is handled after its commit, so the paths are paused in a new transaction.
     * The template is created here and is not a bean, so it does not replace the default one of Spring Boot.
     */
    @Bean
    public DomainEventHandler<SubscriptionExpired> enforcePlanLimitsEventHandler(
            LearningPathCommandService commandService, PlatformTransactionManager transactionManager) {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new EnforcePlanLimitsEventHandler(commandService, newTransaction);
    }

    /** A validated certificate is handled after its commit too, so the paths change in a new transaction. */
    @Bean
    public DomainEventHandler<CertificateVerified> recognizeValidatedCertificateEventHandler(
            LearningPathCommandService commandService, PlatformTransactionManager transactionManager) {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new RecognizeValidatedCertificateEventHandler(commandService, newTransaction);
    }

    /** The redemption is handled after its commit, so the unlock is granted in a new transaction. */
    @Bean
    public DomainEventHandler<AdvancedPathUnlockRedeemed> grantAdvancedPathUnlockEventHandler(
            LearningPathCommandService commandService, PlatformTransactionManager transactionManager) {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new GrantAdvancedPathUnlockEventHandler(commandService, newTransaction);
    }
}
