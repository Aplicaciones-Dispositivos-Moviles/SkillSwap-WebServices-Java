package com.innovify.skillswap.reputation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.AssessmentAttemptPassed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.reputation.domain.repositories.StudentEmployabilityScoreRepository;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The events of Assessment &amp; Peer Review reach the Reputation handlers and end up in the database. */
class ReputationEventWiringTest extends PostgresIntegrationTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private VerifierReliabilityRepository reliabilities;

    @Autowired
    private StudentEmployabilityScoreRepository employabilities;

    @Autowired
    private VerifierProfileRepository profiles;

    @Test
    void passedAttempt_certifiesTheSkillOfTheStudent() {
        publisher.publish(new AssessmentAttemptPassed(1, 3, 10, "http-basics"));
        publisher.publish(new AssessmentAttemptPassed(2, 3, 11, "sql-joins"));

        var score = employabilities.findByStudentId(3).orElseThrow();
        assertThat(score.getVerifiedSkillsCount()).isEqualTo(2);
        assertThat(score.getScore().value()).isEqualTo(20);
    }

    @Test
    void approvedCase_certifiesTheStudentAndCountsForTheVerifier() {
        profiles.save(new VerifierProfile(7, "http-basics"));

        publisher.publish(new VerificationCaseResolved(1, 3, 7, 10, "http-basics",
                CaseType.QUIZ, ReviewDecision.APPROVED, null));

        assertThat(employabilities.findByStudentId(3).orElseThrow().getScore().value()).isEqualTo(10);
        var reliability = reliabilities.findByVerifierUserId(7).orElseThrow();
        assertThat(reliability.getResolvedCasesCount()).isEqualTo(1);
        assertThat(reliability.getScore().value()).isEqualTo(100);
        assertThat(profiles.findByUserId(7).orElseThrow().getRating()).isEqualTo(100);
    }

    @Test
    void rejectedCase_countsForTheVerifierButDoesNotCertify() {
        publisher.publish(new VerificationCaseResolved(1, 3, 7, 10, "http-basics",
                CaseType.QUIZ, ReviewDecision.REJECTED, null));

        assertThat(employabilities.findByStudentId(3)).isEmpty();
        assertThat(reliabilities.findByVerifierUserId(7).orElseThrow().getResolvedCasesCount()).isEqualTo(1);
    }

    @Test
    void overturnedRejection_discountsThePreviousVerifier() {
        profiles.save(new VerifierProfile(5, "http-basics"));
        publisher.publish(new VerificationCaseResolved(1, 3, 5, 10, "http-basics",
                CaseType.QUIZ, ReviewDecision.REJECTED, null));

        publisher.publish(new VerificationCaseResolved(1, 3, 7, 10, "http-basics",
                CaseType.QUIZ, ReviewDecision.APPROVED, 5));

        var previous = reliabilities.findByVerifierUserId(5).orElseThrow();
        assertThat(previous.getOverturnedDecisionsCount()).isEqualTo(1);
        assertThat(previous.getScore().value()).isEqualTo(85);
        assertThat(profiles.findByUserId(5).orElseThrow().getRating()).isEqualTo(85);
        assertThat(reliabilities.findByVerifierUserId(7).orElseThrow().getScore().value()).isEqualTo(100);
        assertThat(employabilities.findByStudentId(3).orElseThrow().getVerifiedSkillsCount()).isEqualTo(1);
    }
}
