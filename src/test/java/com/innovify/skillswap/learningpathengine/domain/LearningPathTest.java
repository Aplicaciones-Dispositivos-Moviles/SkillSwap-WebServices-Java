package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Nodes of {@link TestData#newPath()}: 1 networking-basics, 2 programming-fundamentals, 3 http-basics, 4 rest-api-design, 5 authentication-jwt. */
class LearningPathTest {

    private static List<NodeStatus> statuses(LearningPath path) {
        return path.getNodes().stream().map(PathNode::getStatus).toList();
    }

    // ---- creation ----

    @Test
    void create_startsActiveWithTheNodesInOrder() {
        LearningPath path = TestData.newPath();

        assertThat(path.getStatus()).isEqualTo(PathStatus.ACTIVE);
        assertThat(path.getStudentId()).isEqualTo(1);
        assertThat(path.getNodes()).extracting(PathNode::getOrder).containsExactly(1, 2, 3, 4, 5);
        assertThat(path.getCreatedAt()).isEqualTo(path.getUpdatedAt());
    }

    @Test
    void create_rejectsAnInvalidStudent() {
        LearningPath valid = TestData.newPath();

        assertThatThrownBy(() -> new LearningPath(0, valid.getCareerGoal(), valid.getNodes()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void create_rejectsAnEmptyNodeList() {
        LearningPath valid = TestData.newPath();

        assertThatThrownBy(() -> new LearningPath(1, valid.getCareerGoal(), List.of()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new LearningPath(1, valid.getCareerGoal(), null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void create_rejectsRepeatedOrders() {
        var goal = TestData.goal("sql-fundamentals");
        var nodes = List.of(new PathNode("a", 1, List.of()), new PathNode("b", 1, List.of()));

        assertThatThrownBy(() -> new LearningPath(1, goal, nodes)).isInstanceOf(DomainException.class);
    }

    @Test
    void create_rejectsRepeatedSkills() {
        var goal = TestData.goal("sql-fundamentals");
        var nodes = List.of(new PathNode("a", 1, List.of()), new PathNode("a", 2, List.of()));

        assertThatThrownBy(() -> new LearningPath(1, goal, nodes)).isInstanceOf(DomainException.class);
    }

    @Test
    void create_rejectsPrerequisitesOutsideThePath() {
        var goal = TestData.goal("sql-fundamentals");
        var nodes = List.of(new PathNode("a", 1, List.of("missing")));

        assertThatThrownBy(() -> new LearningPath(1, goal, nodes)).isInstanceOf(DomainException.class);
    }

    @Test
    void create_rejectsCompletedNodes() {
        var goal = TestData.goal("sql-fundamentals");
        PathNode done = new PathNode("a", 1, List.of());
        done.complete();

        assertThatThrownBy(() -> new LearningPath(1, goal, List.of(done))).isInstanceOf(DomainException.class);
    }

    // ---- completeNode ----

    @Test
    void completeNode_unlocksTheNodesWhosePrerequisitesAreAllCompleted() {
        LearningPath path = TestData.newPath();

        // http-basics only needs networking-basics, so it unlocks right away
        path.completeNode(1);
        assertThat(statuses(path)).containsExactly(NodeStatus.COMPLETED, NodeStatus.AVAILABLE, NodeStatus.AVAILABLE,
                NodeStatus.LOCKED, NodeStatus.LOCKED);

        path.completeNode(2);
        assertThat(statuses(path)).containsExactly(NodeStatus.COMPLETED, NodeStatus.COMPLETED, NodeStatus.AVAILABLE,
                NodeStatus.LOCKED, NodeStatus.LOCKED);
    }

    @Test
    void completeNode_keepsLockedTheNodeThatStillMissesAPrerequisite() {
        // rest-api-design needs http-basics and programming-fundamentals
        LearningPath path = TestData.newPath();

        path.completeNode(1).completeNode(2).completeNode(3);

        assertThat(path.getNode(4).orElseThrow().getStatus()).isEqualTo(NodeStatus.AVAILABLE);
        assertThat(path.getNode(5).orElseThrow().getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void completeNode_completesThePathWithTheLastNode() {
        LearningPath path = TestData.newPath();

        for (int id = 1; id <= 5; id++) {
            assertThat(path.getStatus()).isEqualTo(PathStatus.ACTIVE);
            path.completeNode(id);
        }

        assertThat(path.getStatus()).isEqualTo(PathStatus.COMPLETED);
        assertThat(statuses(path)).containsOnly(NodeStatus.COMPLETED);
    }

    @Test
    void completeNode_rejectsALockedNode() {
        LearningPath path = TestData.newPath();

        assertThatThrownBy(() -> path.completeNode(3)).isInstanceOf(DomainException.class)
                .hasMessageContaining("locked");
        assertThat(path.getNode(3).orElseThrow().getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void completeNode_rejectsACompletedNode() {
        LearningPath path = TestData.newPath();
        path.completeNode(1);

        assertThatThrownBy(() -> path.completeNode(1)).isInstanceOf(DomainException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void completeNode_rejectsAnUnknownNode() {
        LearningPath path = TestData.newPath();

        assertThatThrownBy(() -> path.completeNode(99)).isInstanceOf(DomainException.class);
    }

    // ---- getNode ----

    @Test
    void getNode_findsOnlyNodesOfThePath() {
        LearningPath path = TestData.newPath();

        assertThat(path.getNode(2)).get().extracting(PathNode::getSkillTag).isEqualTo("programming-fundamentals");
        assertThat(path.getNode(99)).isEmpty();
    }

    @Test
    void getNode_ignoresNodesThatAreNotSavedYet() {
        LearningPath path = TestData.newUnsavedPath(1, "authentication-jwt");

        assertThat(path.getNode(1)).isEmpty();
    }

    // ---- linkCertificate ----

    @Test
    void linkCertificate_linksTheFirstCertificateOnly() {
        LearningPath path = TestData.newPath();

        assertThat(path.linkCertificate(1, 10)).isTrue();
        assertThat(path.linkCertificate(1, 11)).isFalse();

        assertThat(path.getNode(1).orElseThrow().getLinkedCertificateId()).isEqualTo(10);
    }

    @Test
    void linkCertificate_acceptsLockedNodesAsEvidence() {
        LearningPath path = TestData.newPath();

        assertThat(path.linkCertificate(5, 10)).isTrue();
        assertThat(path.getNode(5).orElseThrow().getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void linkCertificate_neverCompletesTheNode() {
        LearningPath path = TestData.newPath();

        path.linkCertificate(1, 10);

        assertThat(path.getNode(1).orElseThrow().getStatus()).isEqualTo(NodeStatus.AVAILABLE);
    }

    @Test
    void linkCertificate_isIgnoredOnCompletedNodes() {
        LearningPath path = TestData.newPath();
        path.completeNode(1);

        assertThat(path.linkCertificate(1, 10)).isFalse();
        assertThat(path.getNode(1).orElseThrow().getLinkedCertificateId()).isNull();
    }

    @Test
    void linkCertificate_rejectsAnInvalidCertificateOrNode() {
        LearningPath path = TestData.newPath();

        assertThatThrownBy(() -> path.linkCertificate(1, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> path.linkCertificate(99, 10)).isInstanceOf(DomainException.class);
    }

    // ---- linkCertificateToSkill ----

    @Test
    void linkCertificateToSkill_linksTheNodeOfTheSkill() {
        LearningPath path = TestData.newPath();

        assertThat(path.linkCertificateToSkill("http-basics", 10)).isTrue();

        assertThat(path.getNode(3).orElseThrow().getLinkedCertificateId()).isEqualTo(10);
    }

    @Test
    void linkCertificateToSkill_worksOnNodesThatAreNotSavedYet() {
        LearningPath path = TestData.newUnsavedPath(1, "authentication-jwt");

        assertThat(path.linkCertificateToSkill("networking-basics", 10)).isTrue();

        assertThat(path.getNodes().get(0).getLinkedCertificateId()).isEqualTo(10);
    }

    @Test
    void linkCertificateToSkill_returnsFalseForASkillOutsideThePath() {
        LearningPath path = TestData.newPath();

        assertThat(path.linkCertificateToSkill("sql-fundamentals", 10)).isFalse();
    }

    @Test
    void linkCertificateToSkill_keepsTheFirstCertificateAndSkipsCompletedNodes() {
        LearningPath path = TestData.newPath();
        path.completeNode(2);

        assertThat(path.linkCertificateToSkill("networking-basics", 10)).isTrue();
        assertThat(path.linkCertificateToSkill("networking-basics", 11)).isFalse();
        assertThat(path.linkCertificateToSkill("programming-fundamentals", 12)).isFalse();
    }

    @Test
    void linkCertificateToSkill_rejectsAnInvalidCertificate() {
        LearningPath path = TestData.newPath();

        assertThatThrownBy(() -> path.linkCertificateToSkill("http-basics", -1)).isInstanceOf(DomainException.class);
    }

    // ---- pendingPrerequisitesOf ----

    @Test
    void pendingPrerequisitesOf_listsTheUncompletedPrerequisites() {
        LearningPath path = TestData.newPath();

        assertThat(path.pendingPrerequisitesOf(4)).containsExactlyInAnyOrder("http-basics", "programming-fundamentals");

        path.completeNode(2);
        assertThat(path.pendingPrerequisitesOf(4)).containsExactly("http-basics");
    }

    @Test
    void pendingPrerequisitesOf_isEmptyForNodesWithoutPrerequisites() {
        LearningPath path = TestData.newPath();

        assertThat(path.pendingPrerequisitesOf(1)).isEmpty();
    }

    @Test
    void pendingPrerequisitesOf_rejectsAnUnknownNode() {
        LearningPath path = TestData.newPath();

        assertThatThrownBy(() -> path.pendingPrerequisitesOf(99)).isInstanceOf(DomainException.class);
    }

    // ---- attachBlueprint ----

    @Test
    void attachBlueprint_pointsTheAvailableNodeToTheBlueprint() {
        LearningPath path = TestData.newPath();

        path.attachBlueprint(1, 7);

        assertThat(path.getNode(1).orElseThrow().getAssessmentBlueprintId()).isEqualTo(7);
    }

    @Test
    void attachBlueprint_replacesThePreviousBlueprint() {
        LearningPath path = TestData.newPath();

        path.attachBlueprint(1, 7).attachBlueprint(1, 8);

        assertThat(path.getNode(1).orElseThrow().getAssessmentBlueprintId()).isEqualTo(8);
    }

    @Test
    void attachBlueprint_rejectsLockedAndCompletedNodes() {
        LearningPath path = TestData.newPath();
        path.completeNode(1);

        assertThatThrownBy(() -> path.attachBlueprint(4, 7)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> path.attachBlueprint(1, 7)).isInstanceOf(DomainException.class);
    }

    @Test
    void attachBlueprint_rejectsAnInvalidBlueprintOrNode() {
        LearningPath path = TestData.newPath();

        assertThatThrownBy(() -> path.attachBlueprint(1, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> path.attachBlueprint(99, 7)).isInstanceOf(DomainException.class);
    }

    @Test
    void getNodes_returnsACopyThatCannotChangeThePath() {
        LearningPath path = TestData.newPath();
        List<PathNode> copy = new ArrayList<>(path.getNodes());
        copy.clear();

        assertThat(path.getNodes()).hasSize(5);
    }
}
