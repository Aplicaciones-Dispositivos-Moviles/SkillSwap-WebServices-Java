package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.credentialverification.application.acl.CertificateSummary;
import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeCredentialContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import com.innovify.skillswap.shared.application.Result;
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

class LearningPathCommandServiceImplTest {

    private static final String REST_AND_JWT = "I want to build REST APIs with JWT";

    private final FakeCredentialContextFacade credentials = new FakeCredentialContextFacade();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private LearningPathCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        LocaleContextHolder.setLocale(Locale.US);
        service = new LearningPathCommandServiceImpl(paths, FakeSkillTaxonomyMatcher.sample(),
                new DefaultSkillGapAnalyzer(TestData.TAXONOMY), new DefaultLearningPathBuilder(TestData.TAXONOMY),
                credentials, messages);
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

    @Test
    void declare_whenTheStudentHasAnActivePath_returnsActivePathAlreadyExists() {
        declare();

        assertFailure(declare("I want to learn SQL"), LearningPathError.ACTIVE_PATH_ALREADY_EXISTS);
        assertThat(paths.paths()).hasSize(1);
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
