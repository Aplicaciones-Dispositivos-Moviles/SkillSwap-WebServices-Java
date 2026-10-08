package com.innovify.skillswap.learningpathengine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class LearningPathPersistenceTest extends PostgresIntegrationTest {

    @Autowired
    private LearningPathRepository paths;

    @Autowired
    private AssessmentBlueprintRepository blueprints;

    private LearningPath load(int studentId) {
        return paths.findLatestByStudentId(studentId).orElseThrow();
    }

    /** Loads the latest path of a student, applies a change and saves it, as the services do. */
    private void change(int studentId, Consumer<LearningPath> change) {
        LearningPath path = load(studentId);
        change.accept(path);
        paths.save(path);
    }

    private static int nodeId(LearningPath path, String skillTag) {
        return node(path, skillTag).getId();
    }

    private static PathNode node(LearningPath path, String skillTag) {
        return path.getNodes().stream().filter(n -> n.getSkillTag().equals(skillTag)).findFirst().orElseThrow();
    }

    // ---------- Paths ----------

    @Test
    void path_roundTripsTheGoalTheNodesAndTheirPrerequisites() {
        paths.save(TestData.newUnsavedPath(7, "authentication-jwt"));

        LearningPath path = load(7);

        assertThat(path.getId()).isPositive();
        assertThat(path.getStudentId()).isEqualTo(7);
        assertThat(path.getStatus()).isEqualTo(PathStatus.ACTIVE);
        assertThat(path.getCareerGoal().rawText()).isEqualTo("I want to build APIs");
        assertThat(path.getCareerGoal().mappedSkillTags()).containsExactly("authentication-jwt");

        assertThat(path.getNodes()).extracting(PathNode::getSkillTag).containsExactly("networking-basics",
                "programming-fundamentals", "http-basics", "rest-api-design", "authentication-jwt");
        assertThat(path.getNodes()).extracting(PathNode::getOrder).containsExactly(1, 2, 3, 4, 5);
        assertThat(path.getNodes()).extracting(PathNode::getStatus).containsExactly(NodeStatus.AVAILABLE,
                NodeStatus.AVAILABLE, NodeStatus.LOCKED, NodeStatus.LOCKED, NodeStatus.LOCKED);
        assertThat(path.getNodes()).extracting(PathNode::getId).doesNotContainNull();
        assertThat(node(path, "rest-api-design").getPrerequisiteSkillTags())
                .containsExactly("http-basics", "programming-fundamentals");
        assertThat(node(path, "networking-basics").getPrerequisiteSkillTags()).isEmpty();
    }

    @Test
    void linkedCertificate_isPersistedWithoutCompletingTheNode() {
        LearningPath path = TestData.newUnsavedPath(7, "authentication-jwt");
        path.linkCertificateToSkill("http-basics", 42);
        paths.save(path);

        PathNode node = node(load(7), "http-basics");

        assertThat(node.getLinkedCertificateId()).isEqualTo(42);
        assertThat(node.getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    @Test
    void blueprintPointer_isPersisted() {
        paths.save(TestData.newUnsavedPath(7, "authentication-jwt"));
        int nodeId = nodeId(load(7), "networking-basics");

        change(7, path -> path.attachBlueprint(nodeId, 99));

        assertThat(load(7).getNode(nodeId).orElseThrow().getAssessmentBlueprintId()).isEqualTo(99);
    }

    @Test
    void completedNodes_arePersistedAndUnlockTheNextOnes() {
        paths.save(TestData.newUnsavedPath(7, "authentication-jwt"));
        int nodeId = nodeId(load(7), "networking-basics");

        change(7, path -> path.completeNode(nodeId));

        LearningPath path = load(7);
        assertThat(node(path, "networking-basics").getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(node(path, "http-basics").getStatus()).isEqualTo(NodeStatus.AVAILABLE);
        assertThat(node(path, "rest-api-design").getStatus()).isEqualTo(NodeStatus.LOCKED);
        assertThat(path.getUpdatedAt()).isAfterOrEqualTo(path.getCreatedAt());
    }

    // ---------- Queries ----------

    @Test
    void findByNodeId_returnsThePathWithAllItsNodes() {
        paths.save(TestData.newUnsavedPath(7, "authentication-jwt"));
        paths.save(TestData.newUnsavedPath(8, "authentication-jwt"));
        int nodeId = nodeId(load(8), "http-basics");

        LearningPath found = paths.findByNodeId(nodeId).orElseThrow();

        assertThat(found.getStudentId()).isEqualTo(8);
        assertThat(found.getNodes()).hasSize(5);
    }

    @Test
    void findByNodeId_withAnUnknownNode_returnsEmpty() {
        assertThat(paths.findByNodeId(9999)).isEmpty();
    }

    @Test
    void findCompletedSkillTags_returnsOnlyTheCompletedSkillsOfThatStudent() {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
        paths.save(TestData.newUnsavedPath(2, "authentication-jwt"));
        int first = nodeId(load(1), "networking-basics");
        int second = nodeId(load(2), "programming-fundamentals");
        change(1, path -> path.completeNode(first));
        change(2, path -> path.completeNode(second));

        assertThat(paths.findCompletedSkillTagsByStudentId(1)).containsExactly("networking-basics");
    }

    @Test
    void findLatest_returnsTheNewestPathOfTheStudent() {
        paths.save(TestData.newUnsavedPath(1, "networking-basics"));
        change(1, path -> path.completeNode(path.getNodes().get(0).getId()));
        paths.save(TestData.newUnsavedPath(1, "sql-fundamentals"));

        LearningPath latest = load(1);

        assertThat(latest.getNodes()).extracting(PathNode::getSkillTag).containsExactly("sql-fundamentals");
    }

    // ---------- One active path per student ----------

    @Test
    void database_rejectsASecondActivePathOfTheSameStudent() {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));

        assertThatThrownBy(() -> paths.save(TestData.newUnsavedPath(1, "authentication-jwt")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_allowsActivePathsOfDifferentStudents() {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));

        paths.save(TestData.newUnsavedPath(2, "authentication-jwt"));

        assertThat(load(2).getStudentId()).isEqualTo(2);
    }

    @Test
    void database_allowsANewActivePathOnceThePreviousIsCompleted() {
        paths.save(TestData.newUnsavedPath(1, "networking-basics"));
        change(1, path -> path.completeNode(path.getNodes().get(0).getId()));
        assertThat(load(1).getStatus()).isEqualTo(PathStatus.COMPLETED);

        paths.save(TestData.newUnsavedPath(1, "sql-fundamentals"));

        assertThat(load(1).getStatus()).isEqualTo(PathStatus.ACTIVE);
    }

    // ---------- Assessment blueprints ----------

    @Test
    void blueprint_roundTripsTheQuestionsWithTheirCorrectAnswers() {
        AssessmentBlueprint original = new AssessmentBlueprint(7, "http-basics", TestData.questions(5));
        blueprints.save(original);

        AssessmentBlueprint loaded = blueprints.findLatestByPathNodeId(7).orElseThrow();

        assertThat(loaded.getId()).isPositive();
        assertThat(loaded.getSkillTag()).isEqualTo("http-basics");
        assertThat(loaded.getQuestions()).isEqualTo(original.getQuestions());
        assertThat(loaded.getQuestions().get(2).getAnswers()).isEqualTo(original.getQuestions().get(2).getAnswers());
    }

    @Test
    void findLatestBlueprint_returnsTheNewestOneOfTheNode() {
        blueprints.save(new AssessmentBlueprint(7, "http-basics", TestData.questions(5)));
        AssessmentBlueprint newest = new AssessmentBlueprint(7, "http-basics",
                IntStream.rangeClosed(11, 15).mapToObj(TestData::question).toList());
        blueprints.save(newest);
        blueprints.save(new AssessmentBlueprint(8, "sql-fundamentals", TestData.questions(5)));

        AssessmentBlueprint found = blueprints.findLatestByPathNodeId(7).orElseThrow();

        assertThat(found.getId()).isEqualTo(newest.getId());
        assertThat(found.getQuestions().get(0).getQuestionString()).isEqualTo("Question 11?");
    }

    @Test
    void findBlueprintById_withAnUnknownId_returnsEmpty() {
        assertThat(blueprints.findById(9999)).isEmpty();
    }

    // ---------- Schema ----------

    @Test
    void structuredColumns_areStoredAsJsonb() throws SQLException {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
        blueprints.save(new AssessmentBlueprint(7, "http-basics", TestData.questions(5)));

        assertThat(queryString("SELECT jsonb_typeof(career_goal) FROM learning_paths")).isEqualTo("object");
        assertThat(queryString("SELECT career_goal ->> 'rawText' FROM learning_paths"))
                .isEqualTo("I want to build APIs");
        assertThat(queryString("SELECT jsonb_typeof(questions) FROM assessment_blueprints")).isEqualTo("array");
        assertThat(queryString("SELECT jsonb_array_length(questions) FROM assessment_blueprints")).isEqualTo("5");
    }

    @Test
    void statuses_areStoredAsTheTextOfTheCSharpApi() throws SQLException {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));

        assertThat(queryString("SELECT status FROM learning_paths")).isEqualTo("Active");
        assertThat(queryString("SELECT status FROM path_nodes WHERE skill_tag = 'http-basics'")).isEqualTo("Locked");
        assertThat(queryString("SELECT status FROM path_nodes WHERE skill_tag = 'networking-basics'"))
                .isEqualTo("Available");
        assertThat(List.of(queryString("SELECT prerequisites FROM path_nodes WHERE skill_tag = 'rest-api-design'")))
                .containsExactly("http-basics,programming-fundamentals");
    }
}
