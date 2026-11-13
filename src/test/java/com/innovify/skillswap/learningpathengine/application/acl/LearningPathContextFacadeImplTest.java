package com.innovify.skillswap.learningpathengine.application.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeAssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeCertificateSkillAffinityScorer;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeCredentialContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSubscriptionContextFacade;
import com.innovify.skillswap.learningpathengine.application.internal.commandservices.LearningPathCommandServiceImpl;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class LearningPathContextFacadeImplTest {

    private final FakeAssessmentBlueprintRepository blueprints = new FakeAssessmentBlueprintRepository();
    private final FakeMiniProjectRepository miniProjects = new FakeMiniProjectRepository();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private final LearningPathContextFacadeImpl facade;

    LearningPathContextFacadeImplTest() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        var commandService = new LearningPathCommandServiceImpl(paths, FakeSkillTaxonomyMatcher.sample(),
                FakeCertificateSkillAffinityScorer.sample(),
                new DefaultSkillGapAnalyzer(TestData.TAXONOMY), new DefaultLearningPathBuilder(TestData.TAXONOMY), TestData.TAXONOMY,
                new FakeCredentialContextFacade(), new FakeSubscriptionContextFacade(),
                new com.innovify.skillswap.learningpathengine.application.fakes.FakeAdvancedPathUnlockRepository(),
                new com.innovify.skillswap.learningpathengine.application.fakes.FakeRecognitionContextFacade(),
                TransactionOperations.withoutTransaction(), messages);
        facade = new LearningPathContextFacadeImpl(paths, blueprints, miniProjects, commandService);
    }

    private LearningPath addPath(int studentId) {
        return paths.save(TestData.newUnsavedPath(studentId, "authentication-jwt"));
    }

    private static PathNode availableNode(LearningPath path) {
        return path.getNodes().stream().filter(n -> n.getStatus() == NodeStatus.AVAILABLE).findFirst().orElseThrow();
    }

    private AssessmentBlueprint addBlueprint(LearningPath path, PathNode node) {
        AssessmentBlueprint blueprint =
                blueprints.save(new AssessmentBlueprint(node.getId(), node.getSkillTag(), TestData.questions(5)));
        path.attachBlueprint(node.getId(), blueprint.getId());
        return blueprint;
    }

    // ---------- getBlueprint ----------

    @Test
    void getBlueprint_returnsTheDataNeededToGradeAnAttempt() {
        LearningPath path = addPath(7);
        PathNode node = availableNode(path);
        AssessmentBlueprint blueprint = addBlueprint(path, node);

        BlueprintView view = facade.getBlueprint(blueprint.getId()).orElseThrow();

        assertThat(view.blueprintId()).isEqualTo(blueprint.getId());
        assertThat(view.pathNodeId()).isEqualTo(node.getId());
        assertThat(view.studentId()).isEqualTo(7);
        assertThat(view.skillTag()).isEqualTo(node.getSkillTag());
        assertThat(view.isLatest()).isTrue();
        assertThat(view.nodeIsAvailable()).isTrue();
        assertThat(view.questions()).extracting(BlueprintQuestionView::correctAnswer)
                .containsExactlyElementsOf(blueprint.getQuestions().stream().map(Question::getCorrectAnswer).toList());
        assertThat(view.questions()).extracting(BlueprintQuestionView::text)
                .containsExactlyElementsOf(blueprint.getQuestions().stream().map(Question::getQuestionString).toList());
    }

    @Test
    void getBlueprint_includesTheSubTopicOfEachQuestion() {
        LearningPath path = addPath(7);
        PathNode node = availableNode(path);
        List<Question> questions = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(number -> new Question("Question " + number + "?",
                        List.of("a" + number, "b" + number, "c" + number, "d" + number), number % 4,
                        "Topic " + number))
                .toList();
        AssessmentBlueprint blueprint = blueprints.save(
                new AssessmentBlueprint(node.getId(), node.getSkillTag(), questions));
        path.attachBlueprint(node.getId(), blueprint.getId());

        BlueprintView view = facade.getBlueprint(blueprint.getId()).orElseThrow();

        assertThat(view.questions()).extracting(BlueprintQuestionView::subTopic)
                .containsExactly("Topic 1", "Topic 2", "Topic 3", "Topic 4", "Topic 5");
    }

    @Test
    void getBlueprint_forAnUnknownBlueprint_returnsEmpty() {
        assertThat(facade.getBlueprint(999)).isEmpty();
    }

    @Test
    void getBlueprint_ofAnOlderBlueprint_isNotTheLatest() {
        LearningPath path = addPath(1);
        PathNode node = availableNode(path);
        AssessmentBlueprint older = addBlueprint(path, node);
        AssessmentBlueprint latest = addBlueprint(path, node);

        assertThat(facade.getBlueprint(older.getId()).orElseThrow().isLatest()).isFalse();
        assertThat(facade.getBlueprint(latest.getId()).orElseThrow().isLatest()).isTrue();
    }

    @Test
    void getBlueprint_ofACompletedNode_isNoLongerAvailable() {
        LearningPath path = addPath(1);
        PathNode node = availableNode(path);
        AssessmentBlueprint blueprint = addBlueprint(path, node);
        facade.completeNode(node.getId());

        Optional<BlueprintView> view = facade.getBlueprint(blueprint.getId());

        assertThat(view.orElseThrow().nodeIsAvailable()).isFalse();
    }

    @Test
    void getBlueprint_ofAPausedPath_isNotAvailableForANewAttempt() {
        LearningPath path = addPath(1);
        PathNode node = availableNode(path);
        AssessmentBlueprint blueprint = addBlueprint(path, node);
        path.pause();

        assertThat(facade.getBlueprint(blueprint.getId()).orElseThrow().nodeIsAvailable()).isFalse();
    }

    // ---------- completeNode ----------

    @Test
    void completeNode_ofAPausedPath_stillCompletesIt_soAReviewInProgressCounts() {
        LearningPath path = addPath(1);
        PathNode node = availableNode(path);
        path.pause();

        assertThat(facade.completeNode(node.getId())).isEqualTo(NodeCompletionOutcome.COMPLETED);
        assertThat(node.getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(path.isPaused()).isTrue();
    }

    @Test
    void completeNode_ofAnAvailableNode_completesIt() {
        PathNode node = availableNode(addPath(1));

        assertThat(facade.completeNode(node.getId())).isEqualTo(NodeCompletionOutcome.COMPLETED);
        assertThat(node.getStatus()).isEqualTo(NodeStatus.COMPLETED);
    }

    @Test
    void completeNode_ofAnUnknownNode_reportsNodeNotFound() {
        assertThat(facade.completeNode(999)).isEqualTo(NodeCompletionOutcome.NODE_NOT_FOUND);
    }

    @Test
    void completeNode_ofALockedNode_reportsNodeLocked() {
        LearningPath path = addPath(1);
        PathNode locked = path.getNodes().stream().filter(n -> n.getStatus() == NodeStatus.LOCKED).findFirst()
                .orElseThrow();

        assertThat(facade.completeNode(locked.getId())).isEqualTo(NodeCompletionOutcome.NODE_LOCKED);
        assertThat(locked.getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void completeNode_ofACompletedNode_reportsAlreadyCompleted() {
        PathNode node = availableNode(addPath(1));
        facade.completeNode(node.getId());

        assertThat(facade.completeNode(node.getId())).isEqualTo(NodeCompletionOutcome.ALREADY_COMPLETED);
    }

    @Test
    void completeNode_whenSavingFails_reportsFailed() {
        PathNode node = availableNode(addPath(1));
        paths.failOnSave(new DataIntegrityViolationException("failure"));

        assertThat(facade.completeNode(node.getId())).isEqualTo(NodeCompletionOutcome.FAILED);
    }

    // ---------- hasCompletedSkill ----------

    @Test
    void hasCompletedSkill_afterCompletingTheNode_isTrueOnlyForThatStudent() {
        PathNode node = availableNode(addPath(1));
        facade.completeNode(node.getId());

        assertThat(facade.hasCompletedSkill(1, node.getSkillTag())).isTrue();
        assertThat(facade.hasCompletedSkill(2, node.getSkillTag())).isFalse();
    }

    @Test
    void hasCompletedSkill_forANodeStillPending_isFalse() {
        PathNode node = availableNode(addPath(1));

        assertThat(facade.hasCompletedSkill(1, node.getSkillTag())).isFalse();
    }

    // ---------- reopenNode ----------

    @Test
    void reopenNode_ofACompletedNode_makesItAvailableAgain() {
        LearningPath path = addPath(7);
        PathNode node = availableNode(path);
        path.completeNode(node.getId());

        assertThat(facade.reopenNode(node.getId())).isEqualTo(NodeReopenOutcome.REOPENED);
        assertThat(path.getNode(node.getId()).orElseThrow().getStatus()).isEqualTo(NodeStatus.AVAILABLE);
    }

    @Test
    void reopenNode_ofANodeThatIsNotCompleted_reportsNotCompleted() {
        LearningPath path = addPath(7);

        assertThat(facade.reopenNode(availableNode(path).getId())).isEqualTo(NodeReopenOutcome.NOT_COMPLETED);
    }

    @Test
    void reopenNode_ofANodeCompletedWithACertificate_reportsCompletedByCertificate() {
        LearningPath path = addPath(7);
        PathNode node = availableNode(path);
        path.recognizeCertifiedSkill(node.getSkillTag(), 50);

        assertThat(facade.reopenNode(node.getId())).isEqualTo(NodeReopenOutcome.COMPLETED_BY_CERTIFICATE);
    }

    @Test
    void reopenNode_ofAnUnknownNode_reportsNodeNotFound() {
        assertThat(facade.reopenNode(999)).isEqualTo(NodeReopenOutcome.NODE_NOT_FOUND);
    }

    // ---------- getMiniProject ----------

    private MiniProject addMiniProject(PathNode node) {
        return miniProjects.save(new MiniProject(node.getId(), node.getSkillTag(), TestData.miniProjectDraft()));
    }

    @Test
    void getMiniProject_returnsTheDataNeededToReceiveItsDelivery() {
        LearningPath path = addPath(7);
        PathNode node = availableNode(path);
        MiniProject miniProject = addMiniProject(node);

        MiniProjectView view = facade.getMiniProject(miniProject.getId()).orElseThrow();

        assertThat(view.miniProjectId()).isEqualTo(miniProject.getId());
        assertThat(view.pathNodeId()).isEqualTo(node.getId());
        assertThat(view.studentId()).isEqualTo(7);
        assertThat(view.skillTag()).isEqualTo(node.getSkillTag());
        assertThat(view.dueAt()).isEqualTo(miniProject.getDueAt());
        assertThat(view.isLatest()).isTrue();
        assertThat(view.nodeIsAvailable()).isTrue();
    }

    @Test
    void getMiniProject_forAnUnknownMiniProject_returnsEmpty() {
        assertThat(facade.getMiniProject(999)).isEmpty();
    }

    @Test
    void getMiniProject_ofAnOlderMiniProject_isNotTheLatest() {
        LearningPath path = addPath(1);
        PathNode node = availableNode(path);
        MiniProject older = addMiniProject(node);
        MiniProject latest = addMiniProject(node);

        assertThat(facade.getMiniProject(older.getId()).orElseThrow().isLatest()).isFalse();
        assertThat(facade.getMiniProject(latest.getId()).orElseThrow().isLatest()).isTrue();
    }

    @Test
    void getMiniProject_ofACompletedNodeOrAPausedPath_isNotAvailable() {
        LearningPath completedPath = addPath(1);
        PathNode completedNode = availableNode(completedPath);
        MiniProject completed = addMiniProject(completedNode);
        completedPath.completeNode(completedNode.getId());

        LearningPath pausedPath = addPath(2);
        MiniProject paused = addMiniProject(availableNode(pausedPath));
        pausedPath.pause();

        assertThat(facade.getMiniProject(completed.getId()).orElseThrow().nodeIsAvailable()).isFalse();
        assertThat(facade.getMiniProject(paused.getId()).orElseThrow().nodeIsAvailable()).isFalse();
    }

    @Test
    void getMiniProject_whoseNodeNoLongerExists_returnsEmpty() {
        miniProjects.save(new MiniProject(999, "http-basics", TestData.miniProjectDraft()));

        assertThat(facade.getMiniProject(1)).isEmpty();
    }

    // ---------- getMiniProjectRubric ----------

    @Test
    void getMiniProjectRubric_returnsTheStatementTheRubricAndThePassingScore() {
        MiniProject miniProject = addMiniProject(availableNode(addPath(7)));

        MiniProjectRubricView view = facade.getMiniProjectRubric(miniProject.getId()).orElseThrow();

        assertThat(view.miniProjectId()).isEqualTo(miniProject.getId());
        assertThat(view.skillTag()).isEqualTo(miniProject.getSkillTag());
        assertThat(view.title()).isEqualTo("Build a REST API");
        assertThat(view.statement()).isEqualTo("Build a small REST API that manages tasks.");
        assertThat(view.passingScore()).isEqualTo(MiniProject.PASSING_SCORE);
        assertThat(view.rubric()).extracting(RubricCriterionView::name)
                .containsExactlyElementsOf(miniProject.getRubric().stream().map(c -> c.getName()).toList());
        assertThat(view.rubric()).extracting(RubricCriterionView::weight)
                .containsExactlyElementsOf(miniProject.getRubric().stream().map(c -> c.getWeight()).toList());
        assertThat(view.rubric()).allSatisfy(criterion -> assertThat(criterion.description()).isNotBlank());
    }

    @Test
    void getMiniProjectRubric_forAnUnknownMiniProject_returnsEmpty() {
        assertThat(facade.getMiniProjectRubric(999)).isEmpty();
    }

    // ---------- getMostRequestedSkills ----------

    private LearningPath pathCreatedAt(int studentId, Instant createdAt, String... goalTags) {
        LearningPath path = paths.save(TestData.newUnsavedPath(studentId, goalTags));
        ReflectionTestUtils.setField(path, "createdAt", createdAt);
        return path;
    }

    @Test
    void getMostRequestedSkills_countsThePathsOfThePeriodBySkillOfTheGoal() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        pathCreatedAt(1, Instant.parse("2026-10-05T10:00:00Z"), "http-basics");
        pathCreatedAt(2, Instant.parse("2026-10-06T10:00:00Z"), "http-basics", "sql-fundamentals");
        pathCreatedAt(3, Instant.parse("2026-10-07T10:00:00Z"), "sql-fundamentals");
        pathCreatedAt(4, Instant.parse("2026-10-08T10:00:00Z"), "authentication-jwt");
        pathCreatedAt(5, from, "authentication-jwt");
        pathCreatedAt(6, Instant.parse("2026-09-30T23:59:59Z"), "networking-basics");
        pathCreatedAt(7, to, "networking-basics");

        var result = facade.getMostRequestedSkills(from, to, 10);

        assertThat(result).extracting(SkillDemandView::skillTag, SkillDemandView::pathCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("authentication-jwt", 2),
                        org.assertj.core.groups.Tuple.tuple("http-basics", 2),
                        org.assertj.core.groups.Tuple.tuple("sql-fundamentals", 2));
    }

    @Test
    void getMostRequestedSkills_respectsTheLimitAndAnEmptyPeriod() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        pathCreatedAt(1, Instant.parse("2026-10-05T10:00:00Z"), "http-basics");
        pathCreatedAt(2, Instant.parse("2026-10-06T10:00:00Z"), "http-basics");
        pathCreatedAt(3, Instant.parse("2026-10-07T10:00:00Z"), "sql-fundamentals");

        assertThat(facade.getMostRequestedSkills(from, to, 1)).extracting(SkillDemandView::skillTag)
                .containsExactly("http-basics");
        assertThat(facade.getMostRequestedSkills(from, to, 0)).isEmpty();
        assertThat(facade.getMostRequestedSkills(to, from, 10)).isEmpty();
        assertThat(facade.getMostRequestedSkills(null, to, 10)).isEmpty();
        assertThat(facade.getMostRequestedSkills(Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-02-01T00:00:00Z"), 10)).isEmpty();
    }
}
