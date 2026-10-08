package com.innovify.skillswap.learningpathengine.application.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeAssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeCredentialContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.application.internal.commandservices.LearningPathCommandServiceImpl;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;

class LearningPathContextFacadeImplTest {

    private final FakeAssessmentBlueprintRepository blueprints = new FakeAssessmentBlueprintRepository();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private final LearningPathContextFacadeImpl facade;

    LearningPathContextFacadeImplTest() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        var commandService = new LearningPathCommandServiceImpl(paths, FakeSkillTaxonomyMatcher.sample(),
                new DefaultSkillGapAnalyzer(TestData.TAXONOMY), new DefaultLearningPathBuilder(TestData.TAXONOMY),
                new FakeCredentialContextFacade(), messages);
        facade = new LearningPathContextFacadeImpl(paths, blueprints, commandService);
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

    // ---------- completeNode ----------

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
}
