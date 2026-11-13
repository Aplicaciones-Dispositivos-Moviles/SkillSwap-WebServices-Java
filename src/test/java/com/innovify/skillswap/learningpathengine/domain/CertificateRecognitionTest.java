package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Certificates in the domain: a validated certificate completes the node of the skill it covers (US09), a
 * certificate chosen by the student is associated with a node (US15), the coverage threshold, and how repeated
 * questions are recognized (US17). Nodes of {@link TestData#newPath()}: 1 networking-basics,
 * 2 programming-fundamentals, 3 http-basics, 4 rest-api-design, 5 authentication-jwt.
 */
class CertificateRecognitionTest {

    private static PathNode node(LearningPath path, String skillTag) {
        return path.getNodes().stream().filter(n -> n.getSkillTag().equals(skillTag)).findFirst().orElseThrow();
    }

    private static List<NodeStatus> statuses(LearningPath path) {
        return path.getNodes().stream().map(PathNode::getStatus).toList();
    }

    // ---------- Recognize a validated certificate (US09) ----------

    @Test
    @DisplayName("US09: the node of a certified skill is completed and linked to the certificate")
    void recognize_completesTheNodeLinksTheCertificateAndUnlocksTheNext() {
        LearningPath path = TestData.newPath();

        boolean recognized = path.recognizeCertifiedSkill("networking-basics", 40);

        assertThat(recognized).isTrue();
        PathNode networking = node(path, "networking-basics");
        assertThat(networking.getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(networking.getLinkedCertificateId()).isEqualTo(40);
        assertThat(networking.isCompletedByCertificate()).isTrue();
        // http-basics only needed networking-basics.
        assertThat(statuses(path)).containsExactly(NodeStatus.COMPLETED, NodeStatus.AVAILABLE,
                NodeStatus.AVAILABLE, NodeStatus.LOCKED, NodeStatus.LOCKED);
        assertThat(path.getLastProgressAt()).isEqualTo(path.getUpdatedAt());
    }

    @Test
    void recognize_aLockedNode_completesItWithoutItsPrerequisites() {
        LearningPath path = TestData.newPath();

        assertThat(path.recognizeCertifiedSkill("authentication-jwt", 40)).isTrue();

        assertThat(statuses(path)).containsExactly(NodeStatus.AVAILABLE, NodeStatus.AVAILABLE, NodeStatus.LOCKED,
                NodeStatus.LOCKED, NodeStatus.COMPLETED);
        assertThat(path.getStatus()).isEqualTo(PathStatus.ACTIVE);
    }

    @Test
    @DisplayName("US09 escenario 2: completed nodes are kept as they are")
    void recognize_keepsACompletedNodeUnchanged() {
        LearningPath path = TestData.newPath();
        path.completeNode(1);

        assertThat(path.recognizeCertifiedSkill("networking-basics", 40)).isFalse();

        assertThat(node(path, "networking-basics").getLinkedCertificateId()).isNull();
        assertThat(node(path, "networking-basics").isCompletedByCertificate()).isFalse();
    }

    @Test
    void recognize_aSkillOutsideThePath_changesNothing() {
        LearningPath path = TestData.newPath();

        assertThat(path.recognizeCertifiedSkill("sql-fundamentals", 40)).isFalse();
        assertThat(statuses(path)).doesNotContain(NodeStatus.COMPLETED);
    }

    @Test
    void recognize_theLastPendingNode_completesThePath() {
        LearningPath path = TestData.newPath(1, "sql-fundamentals");

        path.recognizeCertifiedSkill("sql-fundamentals", 40);

        assertThat(path.getStatus()).isEqualTo(PathStatus.COMPLETED);
    }

    @Test
    void recognize_worksOnAPausedPathAndOnNodesNotSavedYet() {
        LearningPath unsaved = TestData.newUnsavedPath(1, "authentication-jwt");
        assertThat(unsaved.recognizeCertifiedSkill("rest-api-design", 40)).isTrue();

        LearningPath paused = TestData.newPath().pause();
        assertThat(paused.recognizeCertifiedSkill("rest-api-design", 40)).isTrue();
        assertThat(paused.getStatus()).isEqualTo(PathStatus.PAUSED);
    }

    @Test
    void recognize_onACompletedPathOrWithAnInvalidCertificate_fails() {
        LearningPath path = TestData.newPath(1, "sql-fundamentals");
        assertThatThrownBy(() -> path.recognizeCertifiedSkill("sql-fundamentals", 0))
                .isInstanceOf(DomainException.class);

        path.completeNode(path.getNodes().get(0).getId());
        assertThatThrownBy(() -> path.recognizeCertifiedSkill("sql-fundamentals", 40))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void completedByCertificate_isFalseForANodeCompletedByItsAssessment() {
        LearningPath path = TestData.newPath();
        path.linkCertificate(1, 40);

        path.completeNode(1);

        assertThat(node(path, "networking-basics").getLinkedCertificateId()).isEqualTo(40);
        assertThat(node(path, "networking-basics").isCompletedByCertificate()).isFalse();
    }

    // ---------- Associate a certificate chosen by the student (US15) ----------

    @Test
    void associate_linksTheCertificateReplacingThePreviousOneWithoutCompleting() {
        LearningPath path = TestData.newPath();
        path.linkCertificate(4, 40);

        path.associateCertificate(4, 41);

        assertThat(node(path, "rest-api-design").getLinkedCertificateId()).isEqualTo(41);
        assertThat(node(path, "rest-api-design").getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void associate_toACompletedOrUnknownNode_fails() {
        LearningPath path = TestData.newPath();
        path.completeNode(1);

        assertThatThrownBy(() -> path.associateCertificate(1, 40)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> path.associateCertificate(99, 40)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> path.associateCertificate(2, 0)).isInstanceOf(DomainException.class);
    }

    // ---------- Coverage threshold (US15) ----------

    @ParameterizedTest
    @ValueSource(doubles = {0.7, 0.75, 1.0})
    void affinity_atOrAboveTheThreshold_covers(double score) {
        assertThat(new SkillAffinity("rest-api-design", score).covers()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 0.69})
    void affinity_belowTheThreshold_doesNotCover(double score) {
        assertThat(new SkillAffinity("rest-api-design", score).covers()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.01, Double.NaN})
    void affinity_outsideZeroToOne_isRejected(double score) {
        assertThatThrownBy(() -> new SkillAffinity("rest-api-design", score)).isInstanceOf(DomainException.class);
    }

    @Test
    void affinity_needsASkill() {
        assertThatThrownBy(() -> new SkillAffinity(" ", 1)).isInstanceOf(DomainException.class);
    }

    // ---------- Repeated questions (US17) ----------

    @Test
    void comparableText_ignoresCaseAccentsPunctuationAndSpaces() {
        assertThat(Question.comparableText("¿Qué es  HTTP?")).isEqualTo(Question.comparableText("que es http"));
        assertThat(Question.comparableText("What is REST?")).isNotEqualTo(Question.comparableText("What is HTTP?"));
        assertThat(Question.comparableText(null)).isEmpty();
        assertThat(TestData.question(3).comparableText()).isEqualTo("question 3");
    }
}
