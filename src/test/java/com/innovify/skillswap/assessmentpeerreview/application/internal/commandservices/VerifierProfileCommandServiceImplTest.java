package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentServiceImpl;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CreateVerifierProfileCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.UpdateVerifierAvailabilityCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.shared.application.Result;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

/** User 2 completed "http-basics". */
class VerifierProfileCommandServiceImplTest {

    private static final String SKILL = "http-basics";

    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private VerifierProfileCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        learningPath.addCompletedSkill(2, SKILL);
        LocaleContextHolder.setLocale(Locale.US);
        service = new VerifierProfileCommandServiceImpl(profiles, learningPath,
                new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher()),
                TransactionOperations.withoutTransaction(), TestMessages.source());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Result<VerifierProfile> create(String skill, int userId) {
        return service.handle(new CreateVerifierProfileCommand(userId, skill));
    }

    private Result<VerifierProfile> create(String skill) {
        return create(skill, 2);
    }

    private Result<VerifierProfile> setAvailability(boolean available) {
        return service.handle(new UpdateVerifierAvailabilityCommand(2, available));
    }

    private static void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank();
    }

    // ---------- Create ----------

    @Test
    void create_forACompletedSkill_createsAnAvailableProfile() {
        Result<VerifierProfile> result = create(SKILL);

        assertThat(result.isSuccess()).isTrue();
        assertThat(profiles.profiles()).containsExactly(result.value());
        VerifierProfile profile = result.value();
        assertThat(profile.getVerifierUserId()).isEqualTo(2);
        assertThat(profile.getSkillTags()).containsExactly(SKILL);
        assertThat(profile.isAvailable()).isTrue();
        assertThat(profile.isVerified()).isTrue();
    }

    @Test
    void create_forAnotherCompletedSkill_addsItToTheExistingProfile() {
        learningPath.addCompletedSkill(2, "rest-api-design");
        create(SKILL);

        Result<VerifierProfile> result = create("rest-api-design");

        assertThat(result.isSuccess()).isTrue();
        assertThat(profiles.profiles()).hasSize(1);
        assertThat(result.value().getSkillTags()).containsExactly("http-basics", "rest-api-design");
    }

    @Test
    void create_forASkillAlreadyEnabled_failsWithVerifierSkillAlreadyEnabled() {
        create(SKILL);

        assertFailure(create(SKILL), AssessmentPeerReviewError.VERIFIER_SKILL_ALREADY_ENABLED);
    }

    @Test
    void create_forASkillThatWasNotCompleted_failsWithSkillNotCompleted() {
        assertFailure(create("sql-fundamentals"), AssessmentPeerReviewError.SKILL_NOT_COMPLETED);
        assertThat(profiles.profiles()).isEmpty();
    }

    @Test
    void create_forAnotherStudentsCompletedSkill_failsWithSkillNotCompleted() {
        assertFailure(create(SKILL, 3), AssessmentPeerReviewError.SKILL_NOT_COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void create_withABlankSkill_failsWithInvalidSkillTag(String skill) {
        assertFailure(create(skill), AssessmentPeerReviewError.INVALID_SKILL_TAG);
    }

    @Test
    void create_withANullSkill_failsWithInvalidSkillTag() {
        assertFailure(create(null), AssessmentPeerReviewError.INVALID_SKILL_TAG);
    }

    @Test
    void create_withARevokedProfile_failsWithNotAVerifier() {
        profiles.save(new VerifierProfile(2, "rest-api-design").revoke());

        assertFailure(create(SKILL), AssessmentPeerReviewError.NOT_A_VERIFIER);
    }

    @Test
    void create_assignsThePendingCasesOfTheSkill() {
        VerificationCase pending = cases.save(new VerificationCase(1, 1, 10, SKILL));
        VerificationCase other = cases.save(new VerificationCase(2, 1, 11, "sql-fundamentals"));

        create(SKILL);

        assertThat(pending.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(pending.getVerifierUserId()).isEqualTo(2);
        assertThat(other.getStatus()).isEqualTo(CaseStatus.PENDING);
    }

    @Test
    void create_whenPersistenceFails_failsWithDatabaseError() {
        profiles.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(create(SKILL), AssessmentPeerReviewError.DATABASE_ERROR);
    }

    // ---------- Availability ----------

    @Test
    void setAvailability_switchesItOff() {
        create(SKILL);

        Result<VerifierProfile> result = setAvailability(false);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().isAvailable()).isFalse();
    }

    @Test
    void setAvailability_switchedOn_assignsThePendingCases() {
        profiles.save(new VerifierProfile(2, SKILL).setAvailability(false));
        VerificationCase pending = cases.save(new VerificationCase(1, 1, 10, SKILL));

        Result<VerifierProfile> result = setAvailability(true);

        assertThat(result.value().isAvailable()).isTrue();
        assertThat(pending.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(pending.getVerifierUserId()).isEqualTo(2);
    }

    @Test
    void setAvailability_switchedOff_keepsTheCasesAlreadyAssigned() {
        create(SKILL);
        VerificationCase assigned = cases.save(new VerificationCase(1, 1, 10, SKILL).assignVerifier(2));

        setAvailability(false);

        assertThat(assigned.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(assigned.getVerifierUserId()).isEqualTo(2);
    }

    @Test
    void setAvailability_withoutAProfile_failsWithNotAVerifier() {
        assertFailure(setAvailability(true), AssessmentPeerReviewError.NOT_A_VERIFIER);
    }

    @Test
    void setAvailability_withARevokedProfile_failsWithNotAVerifier() {
        profiles.save(new VerifierProfile(2, SKILL).revoke());

        assertFailure(setAvailability(true), AssessmentPeerReviewError.NOT_A_VERIFIER);
    }

    @Test
    void setAvailability_whenPersistenceFails_failsWithDatabaseError() {
        create(SKILL);
        profiles.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(setAvailability(false), AssessmentPeerReviewError.DATABASE_ERROR);
    }
}
