package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.credentialverification.application.acl.CertificateSummary;
import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeCredentialContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSubscriptionContextFacade;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.EnforcePlanLimitsCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.ResumeLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class LearningPathCommandServiceImplTest {

    private static final String REST_AND_JWT = "I want to build REST APIs with JWT";

    private final FakeCredentialContextFacade credentials = new FakeCredentialContextFacade();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private final FakeSubscriptionContextFacade plans = new FakeSubscriptionContextFacade();
    private int transactions;
    private LearningPathCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        TransactionOperations counting = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactions++;
                return TransactionOperations.withoutTransaction().execute(action);
            }
        };

        LocaleContextHolder.setLocale(Locale.US);
        service = new LearningPathCommandServiceImpl(paths, FakeSkillTaxonomyMatcher.sample(),
                new DefaultSkillGapAnalyzer(TestData.TAXONOMY), new DefaultLearningPathBuilder(TestData.TAXONOMY),
                credentials, plans, counting, messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Result<LearningPath> declare(String text, int studentId) {
        return service.handle(new DeclareGoalCommand(studentId, text));
    }

    private Result<LearningPath> declare(String text) {
        return declare(text, 1);
    }

    private Result<LearningPath> declare() {
        return declare(REST_AND_JWT, 1);
    }

    private Result<LearningPath> refresh() {
        return service.handle(new RefreshCertificateLinksCommand(1));
    }

    private static void assertFailure(Result<?> result, LearningPathError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
    }

    private static int nodeId(LearningPath path, String skillTag) {
        return path.getNodes().stream().filter(n -> n.getSkillTag().equals(skillTag)).findFirst().orElseThrow()
                .getId();
    }

    private static PathNode node(LearningPath path, String skillTag) {
        return path.getNodes().stream().filter(n -> n.getSkillTag().equals(skillTag)).findFirst().orElseThrow();
    }

    /** Declares a goal and completes every node of the resulting path, in order. */
    private void completeWholePath(String goalText) {
        LearningPath path = declare(goalText).value();
        for (PathNode node : path.getNodes()) {
            service.handle(new CompletePathNodeCommand(node.getId()));
        }
        assertThat(path.getStatus()).isEqualTo(PathStatus.COMPLETED);
    }

    // ---------- Declare goal ----------

    @Test
    void declare_withAnInterpretableGoal_createsTheActivePathInPrerequisiteOrder() {
        Result<LearningPath> result = declare();

        assertThat(result.isSuccess()).isTrue();
        assertThat(paths.paths()).hasSize(1);
        LearningPath path = paths.paths().get(0);
        assertThat(path.getStatus()).isEqualTo(PathStatus.ACTIVE);
        assertThat(path.getStudentId()).isEqualTo(1);
        assertThat(path.getCareerGoal().rawText()).isEqualTo(REST_AND_JWT);
        assertThat(path.getNodes()).extracting(PathNode::getSkillTag).containsExactly("networking-basics",
                "programming-fundamentals", "http-basics", "rest-api-design", "authentication-jwt");
        assertThat(path.getNodes()).extracting(PathNode::getStatus).containsExactly(NodeStatus.AVAILABLE,
                NodeStatus.AVAILABLE, NodeStatus.LOCKED, NodeStatus.LOCKED, NodeStatus.LOCKED);
        assertThat(paths.saveCalls()).isEqualTo(1);
    }

    @Test
    void declare_trimsTheGoalText() {
        LearningPath path = declare("   " + REST_AND_JWT + "  ").value();

        assertThat(path.getCareerGoal().rawText()).isEqualTo(REST_AND_JWT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void declare_withBlankText_returnsInvalidGoal(String text) {
        assertFailure(declare(text), LearningPathError.INVALID_GOAL);
        assertThat(paths.paths()).isEmpty();
    }

    @Test
    void declare_withNullText_returnsInvalidGoal() {
        assertFailure(declare(null), LearningPathError.INVALID_GOAL);
    }

    @Test
    void declare_withTextOverTheLimit_returnsInvalidGoal() {
        assertFailure(declare("a".repeat(CareerGoal.MAX_RAW_TEXT_LENGTH + 1)), LearningPathError.INVALID_GOAL);
    }

    @Test
    void declare_whenNoSkillMatches_returnsGoalNotInterpretableAndSavesNothing() {
        assertFailure(declare("I want to bake cakes"), LearningPathError.GOAL_NOT_INTERPRETABLE);
        assertThat(paths.paths()).isEmpty();
        assertThat(paths.saveCalls()).isZero();
    }

    // ---------- Plan limits ----------

    /** A saved path of the student with that status. */
    private LearningPath seedPath(int studentId, PathStatus status) {
        LearningPath path = paths.save(TestData.newUnsavedPath(studentId, "http-basics"));
        if (status == PathStatus.PAUSED) {
            path.pause();
        } else if (status == PathStatus.COMPLETED) {
            ReflectionTestUtils.setField(path, "status", PathStatus.COMPLETED);
        }
        return path;
    }

    private static void assertPlanLimit(Result<?> result, String limit, String plan, int max, int current) {
        assertFailure(result, LearningPathError.PLAN_LIMIT_REACHED);
        assertThat(result.details()).containsEntry("limit", limit)
                .containsEntry("plan", plan)
                .containsEntry("max", max)
                .containsEntry("current", current)
                .containsEntry("upgradeAvailable", "Free".equals(plan));
    }

    @Test
    void declare_onTheFreePlanWithAnActivePath_returnsPlanLimitReachedForTheActiveRoutes() {
        declare();

        Result<LearningPath> result = declare("I want to learn SQL");

        assertPlanLimit(result, "ActiveRoutes", "Free", 1, 1);
        assertThat(paths.paths()).hasSize(1);
    }

    @Test
    void declare_onTheFreePlanWithThreePaths_returnsPlanLimitReachedForTheTotalRoutes() {
        seedPath(1, PathStatus.PAUSED);
        seedPath(1, PathStatus.COMPLETED);
        seedPath(1, PathStatus.PAUSED);

        Result<LearningPath> result = declare();

        assertPlanLimit(result, "TotalRoutes", "Free", 3, 3);
        assertThat(paths.paths()).hasSize(3);
    }

    @Test
    void declare_onTheFreePlanWithAPausedPath_isAllowedWhileUnderTheTotal() {
        seedPath(1, PathStatus.PAUSED);

        Result<LearningPath> result = declare();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(PathStatus.ACTIVE);
    }

    @Test
    void declare_onThePaidPlan_allowsThreeActivePathsAndNoTotalCap() {
        plans.premium(1);
        seedPath(1, PathStatus.COMPLETED);
        seedPath(1, PathStatus.COMPLETED);
        seedPath(1, PathStatus.PAUSED);
        seedPath(1, PathStatus.ACTIVE);
        seedPath(1, PathStatus.ACTIVE);

        assertThat(declare().isSuccess()).isTrue();
        assertPlanLimit(declare("I want to learn SQL"), "ActiveRoutes", "Premium", 3, 3);
        assertThat(paths.countByStudentId(1)).isEqualTo(6);
    }

    @Test
    void declare_checksTheLimitAgainUnderTheLockOfTheStudent() {
        Result<LearningPath> result = declare();

        assertThat(result.isSuccess()).isTrue();
        assertThat(paths.lockedStudents()).containsExactly(1);
        assertThat(transactions).isEqualTo(1);
    }

    @Test
    void declare_overTheLimit_doesNotInterpretTheGoalNorLock() {
        declare();
        paths.lockedStudents().clear();

        assertFailure(declare("this matches no skill at all"), LearningPathError.PLAN_LIMIT_REACHED);
        assertThat(paths.lockedStudents()).isEmpty();
    }

    // ---------- Pause and resume ----------

    @Test
    void pause_anActivePath_pausesItAndKeepsItsNodes() {
        LearningPath path = declare().value();
        List<NodeStatus> before = path.getNodes().stream().map(PathNode::getStatus).toList();

        Result<LearningPath> result = service.handle(new PauseLearningPathCommand(path.getId(), 1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(PathStatus.PAUSED);
        assertThat(result.value().getNodes()).extracting(PathNode::getStatus).containsExactlyElementsOf(before);
    }

    @Test
    void pause_aPathThatIsNotActive_returnsPathNotActive() {
        LearningPath paused = seedPath(1, PathStatus.PAUSED);
        LearningPath completed = seedPath(1, PathStatus.COMPLETED);

        assertFailure(service.handle(new PauseLearningPathCommand(paused.getId(), 1)),
                LearningPathError.PATH_NOT_ACTIVE);
        assertFailure(service.handle(new PauseLearningPathCommand(completed.getId(), 1)),
                LearningPathError.PATH_NOT_ACTIVE);
    }

    @Test
    void pause_thePathOfAnotherStudent_returnsNotPathOwner() {
        LearningPath path = seedPath(2, PathStatus.ACTIVE);

        assertFailure(service.handle(new PauseLearningPathCommand(path.getId(), 1)), LearningPathError.NOT_PATH_OWNER);
        assertThat(path.isActive()).isTrue();
    }

    @Test
    void pauseOrResume_anUnknownPath_returnsPathNotFound() {
        assertFailure(service.handle(new PauseLearningPathCommand(99, 1)), LearningPathError.PATH_NOT_FOUND);
        assertFailure(service.handle(new ResumeLearningPathCommand(99, 1)), LearningPathError.PATH_NOT_FOUND);
    }

    @Test
    void resume_onTheFreePlan_needsTheActivePathToBePausedFirst() {
        LearningPath paused = seedPath(1, PathStatus.PAUSED);
        LearningPath active = seedPath(1, PathStatus.ACTIVE);

        assertPlanLimit(service.handle(new ResumeLearningPathCommand(paused.getId(), 1)), "ActiveRoutes", "Free", 1,
                1);
        assertThat(paused.isPaused()).isTrue();

        service.handle(new PauseLearningPathCommand(active.getId(), 1));
        Result<LearningPath> resumed = service.handle(new ResumeLearningPathCommand(paused.getId(), 1));

        assertThat(resumed.isSuccess()).isTrue();
        assertThat(paused.isActive()).isTrue();
        assertThat(active.isPaused()).isTrue();
        assertThat(paths.lockedStudents()).contains(1);
    }

    @Test
    void resume_onThePaidPlan_allowsUpToThreeActivePaths() {
        plans.premium(1);
        seedPath(1, PathStatus.ACTIVE);
        LearningPath second = seedPath(1, PathStatus.PAUSED);

        assertThat(service.handle(new ResumeLearningPathCommand(second.getId(), 1)).isSuccess()).isTrue();
    }

    @Test
    void resume_aPathThatIsNotPaused_returnsPathNotPaused() {
        LearningPath active = seedPath(1, PathStatus.ACTIVE);

        assertFailure(service.handle(new ResumeLearningPathCommand(active.getId(), 1)),
                LearningPathError.PATH_NOT_PAUSED);
    }

    @Test
    void resume_thePathOfAnotherStudent_returnsNotPathOwner() {
        LearningPath path = seedPath(2, PathStatus.PAUSED);

        assertFailure(service.handle(new ResumeLearningPathCommand(path.getId(), 1)),
                LearningPathError.NOT_PATH_OWNER);
    }

    // ---------- Downgrade ----------

    private static void progressedAt(LearningPath path, Instant moment) {
        ReflectionTestUtils.setField(path, "lastProgressAt", moment);
    }

    @Test
    void enforcePlanLimits_onTheFreePlan_keepsTheMostRecentProgressActiveAndPausesTheRest() {
        Instant now = Instant.now();
        LearningPath oldest = seedPath(1, PathStatus.ACTIVE);
        LearningPath mostRecent = seedPath(1, PathStatus.ACTIVE);
        LearningPath middle = seedPath(1, PathStatus.ACTIVE);
        LearningPath completed = seedPath(1, PathStatus.COMPLETED);
        progressedAt(oldest, now.minus(Duration.ofDays(9)));
        progressedAt(mostRecent, now.minus(Duration.ofMinutes(5)));
        progressedAt(middle, now.minus(Duration.ofDays(1)));

        Result<List<LearningPath>> result = service.handle(new EnforcePlanLimitsCommand(1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).containsExactlyInAnyOrder(oldest, middle);
        assertThat(mostRecent.isActive()).isTrue();
        assertThat(oldest.isPaused()).isTrue();
        assertThat(middle.isPaused()).isTrue();
        assertThat(completed.getStatus()).isEqualTo(PathStatus.COMPLETED);
        assertThat(paths.paths()).hasSize(4);
        assertThat(paths.lockedStudents()).containsExactly(1);
    }

    @Test
    void enforcePlanLimits_withinTheLimit_changesNothing() {
        LearningPath active = seedPath(1, PathStatus.ACTIVE);
        seedPath(1, PathStatus.PAUSED);

        Result<List<LearningPath>> result = service.handle(new EnforcePlanLimitsCommand(1));

        assertThat(result.value()).isEmpty();
        assertThat(active.isActive()).isTrue();
    }

    @Test
    void enforcePlanLimits_onThePaidPlan_keepsThreeActive() {
        plans.premium(1);
        for (int i = 0; i < 4; i++) {
            seedPath(1, PathStatus.ACTIVE);
        }

        assertThat(service.handle(new EnforcePlanLimitsCommand(1)).value()).hasSize(1);
        assertThat(paths.countActiveByStudentId(1)).isEqualTo(3);
    }

    @Test
    void enforcePlanLimits_whenSavingFails_returnsDatabaseError() {
        seedPath(1, PathStatus.ACTIVE);
        seedPath(1, PathStatus.ACTIVE);
        paths.failOnSave(new org.springframework.dao.DataAccessResourceFailureException("down"));

        assertFailure(service.handle(new EnforcePlanLimitsCommand(1)), LearningPathError.DATABASE_ERROR);
    }

    @Test
    void declare_whenAnotherStudentHasAnActivePath_isAllowed() {
        declare(REST_AND_JWT, 2);

        Result<LearningPath> result = declare(REST_AND_JWT, 1);

        assertThat(result.isSuccess()).isTrue();
        assertThat(paths.paths()).hasSize(2);
    }

    @Test
    void declare_afterCompletingThePreviousPath_skipsTheSkillsAlreadyDemonstrated() {
        completeWholePath("I want to learn HTTP");

        Result<LearningPath> result = declare("I want REST APIs");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getNodes()).extracting(PathNode::getSkillTag)
                .containsExactly("programming-fundamentals", "rest-api-design");
        assertThat(result.value().getNodes()).extracting(PathNode::getStatus)
                .containsExactly(NodeStatus.AVAILABLE, NodeStatus.LOCKED);
    }

    @Test
    void declare_whenEveryRequiredSkillWasAlreadyDemonstrated_returnsGoalAlreadyAchieved() {
        completeWholePath("I want to learn HTTP");

        assertFailure(declare("I want to learn HTTP again"), LearningPathError.GOAL_ALREADY_ACHIEVED);
        assertThat(paths.paths()).hasSize(1);
    }

    // ---------- Declare goal: certificates as evidence ----------

    @Test
    void declare_linksTheCertificatesWhoseCourseMatchesASkillWithoutCompletingTheNode() {
        credentials.certificates().addAll(List.of(
                new CertificateSummary(10, "Building REST services", "Coursera"),
                new CertificateSummary(11, null, "Udemy"),
                new CertificateSummary(12, "Cooking basics", "Udemy")));

        LearningPath path = declare().value();

        List<PathNode> linked = path.getNodes().stream().filter(n -> n.getLinkedCertificateId() != null).toList();
        assertThat(linked).hasSize(1);
        assertThat(linked.get(0).getSkillTag()).isEqualTo("rest-api-design");
        assertThat(linked.get(0).getLinkedCertificateId()).isEqualTo(10);
        assertThat(linked.get(0).getStatus()).isEqualTo(NodeStatus.LOCKED);
        assertThat(path.getNodes()).extracting(PathNode::getStatus).doesNotContain(NodeStatus.COMPLETED);
    }

    @Test
    void declare_keepsTheFirstCertificateWhenSeveralMatchTheSameSkill() {
        credentials.certificates().addAll(List.of(
                new CertificateSummary(21, "Advanced REST", "Udemy"),
                new CertificateSummary(20, "REST fundamentals", "Coursera")));

        LearningPath path = declare().value();

        assertThat(node(path, "rest-api-design").getLinkedCertificateId()).isEqualTo(20);
    }

    @Test
    void declare_whenTheCertificateLookupFails_stillCreatesThePathWithoutLinks() {
        credentials.failWith(new IllegalStateException("credential context unavailable"));

        Result<LearningPath> result = declare();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getNodes()).extracting(PathNode::getLinkedCertificateId).containsOnlyNulls();
    }

    // ---------- Declare goal: infrastructure failures ----------

    @Test
    void declare_whenSavingFails_returnsDatabaseError() {
        paths.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(declare(), LearningPathError.DATABASE_ERROR);
    }

    @Test
    void declare_whenSomethingUnexpectedFails_returnsInternalServerError() {
        paths.failOnSave(new IllegalStateException("boom"));

        assertFailure(declare(), LearningPathError.INTERNAL_SERVER_ERROR);
    }

    // ---------- Complete node ----------

    @Test
    void completeNode_onAnAvailableNode_completesItAndUnlocksTheNext() {
        LearningPath path = declare("I want to learn HTTP").value();

        Result<LearningPath> result = service.handle(new CompletePathNodeCommand(nodeId(path, "networking-basics")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(node(path, "networking-basics").getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(node(path, "http-basics").getStatus()).isEqualTo(NodeStatus.AVAILABLE);
        assertThat(path.getStatus()).isEqualTo(PathStatus.ACTIVE);
    }

    @Test
    void completeNode_onTheLastNode_completesThePath() {
        completeWholePath("I want to learn HTTP");

        assertThat(paths.paths().get(0).getStatus()).isEqualTo(PathStatus.COMPLETED);
    }

    @Test
    void completeNode_onALockedNode_returnsNodeLockedListingThePendingPrerequisites() {
        LearningPath path = declare().value();

        Result<LearningPath> result = service.handle(new CompletePathNodeCommand(nodeId(path, "rest-api-design")));

        assertFailure(result, LearningPathError.NODE_LOCKED);
        assertThat(result.details().get("pendingPrerequisites")).asList()
                .containsExactlyInAnyOrder("http-basics", "programming-fundamentals");
        assertThat(node(path, "rest-api-design").getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void completeNode_twice_returnsNodeAlreadyCompleted() {
        LearningPath path = declare().value();
        int nodeId = nodeId(path, "networking-basics");
        service.handle(new CompletePathNodeCommand(nodeId));

        assertFailure(service.handle(new CompletePathNodeCommand(nodeId)), LearningPathError.NODE_ALREADY_COMPLETED);
    }

    @Test
    void completeNode_forAnUnknownNode_returnsNodeNotFound() {
        assertFailure(service.handle(new CompletePathNodeCommand(99)), LearningPathError.NODE_NOT_FOUND);
    }

    @Test
    void completeNode_whenSavingFails_returnsDatabaseError() {
        LearningPath path = declare().value();
        paths.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(service.handle(new CompletePathNodeCommand(nodeId(path, "networking-basics"))),
                LearningPathError.DATABASE_ERROR);
    }

    // ---------- Refresh certificate links ----------

    @Test
    void refresh_linksACertificateUploadedAfterThePathWasCreated() {
        declare();
        credentials.certificates().add(new CertificateSummary(30, "REST fundamentals", "Coursera"));
        int savesBefore = paths.saveCalls();

        Result<LearningPath> result = refresh();

        assertThat(result.isSuccess()).isTrue();
        PathNode node = node(result.value(), "rest-api-design");
        assertThat(node.getLinkedCertificateId()).isEqualTo(30);
        assertThat(node.getStatus()).isEqualTo(NodeStatus.LOCKED);
        assertThat(paths.saveCalls()).isEqualTo(savesBefore + 1);
    }

    @Test
    void refresh_withNothingNewToLink_doesNotSave() {
        declare();
        int savesBefore = paths.saveCalls();

        assertThat(refresh().isSuccess()).isTrue();
        assertThat(paths.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void refresh_keepsTheCertificateAlreadyLinked() {
        credentials.certificates().add(new CertificateSummary(10, "REST basics", null));
        declare();
        credentials.certificates().add(new CertificateSummary(11, "Advanced REST", null));
        int savesBefore = paths.saveCalls();

        Result<LearningPath> result = refresh();

        assertThat(node(result.value(), "rest-api-design").getLinkedCertificateId()).isEqualTo(10);
        assertThat(paths.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void refresh_forAStudentWithoutPath_returnsPathNotFound() {
        assertFailure(refresh(), LearningPathError.PATH_NOT_FOUND);
    }

    @Test
    void refresh_onACompletedPath_changesNothing() {
        completeWholePath("I want to learn HTTP");
        credentials.certificates().add(new CertificateSummary(30, "HTTP essentials", null));
        int savesBefore = paths.saveCalls();

        Result<LearningPath> result = refresh();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getNodes()).extracting(PathNode::getLinkedCertificateId).containsOnlyNulls();
        assertThat(paths.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void refresh_whenTheCertificateLookupFails_stillReturnsThePath() {
        declare();
        credentials.failWith(new IllegalStateException("credential context unavailable"));

        assertThat(refresh().isSuccess()).isTrue();
    }

    @Test
    void refresh_whenSavingTheLinksFails_stillReturnsThePath() {
        declare();
        credentials.certificates().add(new CertificateSummary(30, "REST fundamentals", null));
        paths.failOnSave(new DataIntegrityViolationException("failure"));

        assertThat(refresh().isSuccess()).isTrue();
    }

    // ---------- Messages ----------

    @Test
    void failures_areLocalized() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));

        Result<LearningPath> result = declare("");

        assertThat(result.message()).isEqualTo("La meta debe tener entre 1 y 500 caracteres.");
    }
}
