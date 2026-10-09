package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeReviewDeadlinePolicyRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.DefineReviewDeadlinesCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.support.TransactionOperations;

class ReviewDeadlinePolicyCommandServiceImplTest {

    private static final int SENIOR = 7;

    private final FakeReviewDeadlinePolicyRepository policies = new FakeReviewDeadlinePolicyRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final Set<Integer> seniors = new HashSet<>();
    private ReviewDeadlinePolicyCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        ReputationContextFacade reputation = new ReputationContextFacade() {
            @Override
            public boolean isSeniorVerifier(int userId) {
                return seniors.contains(userId);
            }

            @Override
            public Set<Integer> findSeniorVerifiers(Collection<Integer> userIds) {
                return userIds.stream().filter(seniors::contains).collect(Collectors.toSet());
            }
        };
        service = new ReviewDeadlinePolicyCommandServiceImpl(policies, profiles, reputation,
                TransactionOperations.withoutTransaction(), TestMessages.source());
        profiles.save(new VerifierProfile(SENIOR, "http-basics"));
        seniors.add(SENIOR);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void define_byASenior_savesBothPlans() {
        var result = service.handle(new DefineReviewDeadlinesCommand(SENIOR, 24, 3));

        assertThat(result.isSuccess()).isTrue();
        assertThat(policies.findByPlan("Premium").orElseThrow().getDeadline()).isEqualTo(ReviewDeadline.hours(24));
        assertThat(policies.findByPlan("Free").orElseThrow().getDeadline()).isEqualTo(ReviewDeadline.businessDays(3));

        service.handle(new DefineReviewDeadlinesCommand(SENIOR, 48, 5));
        assertThat(policies.policies()).hasSize(2);
        assertThat(policies.findByPlan("Premium").orElseThrow().getDeadline()).isEqualTo(ReviewDeadline.hours(48));
    }

    @Test
    void define_byWhoIsNotASeniorOrNotAnEnabledVerifier_isForbidden() {
        profiles.save(new VerifierProfile(8, "http-basics"));
        seniors.add(9);

        assertThat(service.handle(new DefineReviewDeadlinesCommand(8, 24, 3)).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertThat(service.handle(new DefineReviewDeadlinesCommand(9, 24, 3)).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        profiles.findByUserId(SENIOR).orElseThrow().revoke();
        assertThat(service.handle(new DefineReviewDeadlinesCommand(SENIOR, 24, 3)).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertThat(policies.policies()).isEmpty();
    }

    @Test
    void define_outOfRange_isInvalid() {
        for (DefineReviewDeadlinesCommand command : java.util.List.of(
                new DefineReviewDeadlinesCommand(SENIOR, 49, 5), new DefineReviewDeadlinesCommand(SENIOR, 48, 6),
                new DefineReviewDeadlinesCommand(SENIOR, null, 5), new DefineReviewDeadlinesCommand(SENIOR, 24, null),
                new DefineReviewDeadlinesCommand(SENIOR, -1, 5))) {
            assertThat(service.handle(command).error()).as(command.toString())
                    .isEqualTo(AssessmentPeerReviewError.INVALID_REVIEW_DEADLINE);
        }
        assertThat(policies.policies()).isEmpty();
    }
}
