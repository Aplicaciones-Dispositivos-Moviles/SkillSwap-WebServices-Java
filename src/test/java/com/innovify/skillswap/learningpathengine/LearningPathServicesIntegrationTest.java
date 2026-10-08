package com.innovify.skillswap.learningpathengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import com.innovify.skillswap.learningpathengine.application.commandservices.AssessmentBlueprintCommandService;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.application.queryservices.AssessmentBlueprintQueryService;
import com.innovify.skillswap.learningpathengine.application.queryservices.LearningPathQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateAssessmentBlueprintCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetAssessmentBlueprintByPathNodeIdQuery;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetLearningPathByStudentIdQuery;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;

/** The Learning Path services against the real Spring wiring, the real skill catalog and a real PostgreSQL. */
class LearningPathServicesIntegrationTest extends PostgresIntegrationTest {

    private static final String REST_AND_JWT = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final int STUDENT = 1;

    @Autowired
    private LearningPathCommandService commands;

    @Autowired
    private AssessmentBlueprintCommandService blueprintCommands;

    @Autowired
    private LearningPathQueryService queries;

    @Autowired
    private AssessmentBlueprintQueryService blueprintQueries;

    @Autowired
    private SkillTaxonomy taxonomy;

    @Autowired
    private QuestionGenerationService questionGeneration;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private CertificateRepository certificates;

    private Result<LearningPath> declare(String text) {
        return declare(text, STUDENT);
    }

    private Result<LearningPath> declare(String text, int studentId) {
        return commands.handle(new DeclareGoalCommand(studentId, text));
    }

    private LearningPath pathOf(int studentId) {
        return queries.handle(new GetLearningPathByStudentIdQuery(studentId)).orElseThrow();
    }

    private LearningPath pathOf() {
        return pathOf(STUDENT);
    }

    private Result<LearningPath> complete(int nodeId) {
        return commands.handle(new CompletePathNodeCommand(nodeId));
    }

    private Result<AssessmentBlueprint> generate(int nodeId, int studentId) {
        return blueprintCommands.handle(new GenerateAssessmentBlueprintCommand(nodeId, studentId));
    }

    private static PathNode node(LearningPath path, String skillTag) {
        return path.getNodes().stream().filter(n -> n.getSkillTag().equals(skillTag)).findFirst().orElseThrow();
    }

    private static int nodeId(LearningPath path, String skillTag) {
        return node(path, skillTag).getId();
    }

    // ---------- Wiring ----------

    @Test
    void everyLearningPathServiceIsRegisteredInTheContainer() {
        assertThat(commands).isNotNull();
        assertThat(blueprintCommands).isNotNull();
        assertThat(queries).isNotNull();
        assertThat(blueprintQueries).isNotNull();
        assertThat(taxonomy).isNotNull();
        assertThat(transactions).isNotNull();
        assertThat(questionGeneration).isInstanceOf(FakeQuestionGenerationService.class);
    }

    // ---------- Declaring a goal ----------

    @Test
    void declare_persistsThePathBuiltFromTheRealCatalog() {
        Result<LearningPath> result = declare(REST_AND_JWT);

        assertThat(result.isSuccess()).as(result.message()).isTrue();
        LearningPath path = pathOf();
        assertThat(path.getNodes().stream().map(PathNode::getSkillTag).toList()).containsExactly(
                "networking-basics", "programming-fundamentals", "http-basics", "rest-api-design",
                "authentication-jwt");
        assertThat(path.getNodes().stream().map(PathNode::getStatus).toList()).containsExactly(
                NodeStatus.AVAILABLE, NodeStatus.AVAILABLE, NodeStatus.LOCKED, NodeStatus.LOCKED,
                NodeStatus.LOCKED);
        assertThat(path.getCareerGoal().mappedSkillTags())
                .containsExactlyInAnyOrder("authentication-jwt", "rest-api-design");
    }

    @Test
    void declare_withAGoalNoSkillMatches_isRejectedAndSavesNothing() {
        Result<LearningPath> result = declare("quiero cocinar pasteles");

        assertThat(result.error().name()).isEqualTo("GOAL_NOT_INTERPRETABLE");
        assertThat(queries.handle(new GetLearningPathByStudentIdQuery(STUDENT))).isEmpty();
    }

    @Test
    void declare_twice_returnsActivePathAlreadyExists() {
        declare(REST_AND_JWT);

        Result<LearningPath> result = declare("quiero aprender SQL");

        assertThat(result.error().name()).isEqualTo("ACTIVE_PATH_ALREADY_EXISTS");
    }

    @Test
    void declare_linksAnExistingCertificateToTheNodeOfItsCourse() {
        Certificate certificate = new Certificate(STUDENT, "file-hash", "ref/file-hash")
                .applyExtractedData(null, "Coursera", "REST API fundamentals", null, null, null, null, null, null,
                        "")
                .assessRisk(new RiskAssessment(0));
        int certificateId = certificates.save(certificate).getId();

        declare(REST_AND_JWT);

        LearningPath path = pathOf();
        List<PathNode> linked = path.getNodes().stream().filter(n -> n.getLinkedCertificateId() != null).toList();
        assertThat(linked).hasSize(1);
        assertThat(linked.get(0).getSkillTag()).isEqualTo("rest-api-design");
        assertThat(linked.get(0).getLinkedCertificateId()).isEqualTo(certificateId);
        assertThat(linked.get(0).getStatus()).isEqualTo(NodeStatus.LOCKED);
    }

    // ---------- Assessments ----------

    @Test
    void generateBlueprint_forAnAvailableNode_persistsItAndPointsTheNodeToIt() {
        declare(REST_AND_JWT);
        int nodeId = nodeId(pathOf(), "networking-basics");

        Result<AssessmentBlueprint> result = generate(nodeId, STUDENT);

        assertThat(result.isSuccess()).as(result.message()).isTrue();
        assertThat(node(pathOf(), "networking-basics").getAssessmentBlueprintId()).isEqualTo(result.value().getId());

        Optional<AssessmentBlueprint> stored =
                blueprintQueries.handle(new GetAssessmentBlueprintByPathNodeIdQuery(nodeId));
        assertThat(stored).isPresent();
        assertThat(stored.get().getQuestions()).hasSize(5);
        assertThat(stored.get().getSkillTag()).isEqualTo("networking-basics");
    }

    @Test
    void generateBlueprint_again_replacesThePointerAndKeepsTheHistory() {
        declare(REST_AND_JWT);
        int nodeId = nodeId(pathOf(), "networking-basics");
        AssessmentBlueprint first = generate(nodeId, STUDENT).value();

        AssessmentBlueprint second = generate(nodeId, STUDENT).value();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(node(pathOf(), "networking-basics").getAssessmentBlueprintId()).isEqualTo(second.getId());
        assertThat(blueprintQueries.handle(new GetAssessmentBlueprintByPathNodeIdQuery(nodeId)))
                .hasValueSatisfying(latest -> assertThat(latest.getId()).isEqualTo(second.getId()));
    }

    @Test
    void generateBlueprint_forALockedNode_listsThePendingPrerequisites() {
        declare(REST_AND_JWT);
        int nodeId = nodeId(pathOf(), "rest-api-design");

        Result<AssessmentBlueprint> result = generate(nodeId, STUDENT);

        assertThat(result.error().name()).isEqualTo("NODE_LOCKED");
        assertThat(result.details().get("pendingPrerequisites")).asList()
                .containsExactlyInAnyOrder("http-basics", "programming-fundamentals");
    }

    @Test
    void generateBlueprint_byAnotherStudent_isRejected() {
        declare(REST_AND_JWT);
        int nodeId = nodeId(pathOf(), "networking-basics");

        Result<AssessmentBlueprint> result = generate(nodeId, 2);

        assertThat(result.error().name()).isEqualTo("NOT_PATH_OWNER");
    }

    // ---------- Completing nodes and reusing what was demonstrated ----------

    @Test
    void completingTheWholePath_allowsANewGoalThatReusesWhatWasDemonstrated() {
        declare(REST_AND_JWT);
        for (String skill : List.of("networking-basics", "programming-fundamentals", "http-basics",
                "rest-api-design", "authentication-jwt")) {
            Result<LearningPath> completed = complete(nodeId(pathOf(), skill));
            assertThat(completed.isSuccess()).as(skill + ": " + completed.message()).isTrue();
        }
        assertThat(pathOf().getStatus()).isEqualTo(PathStatus.COMPLETED);

        Result<LearningPath> repeated = declare("quiero aprender APIs REST");
        Result<LearningPath> next = declare("quiero aprender Spring Boot");

        assertThat(repeated.error().name()).isEqualTo("GOAL_ALREADY_ACHIEVED");
        assertThat(next.isSuccess()).as(next.message()).isTrue();
        LearningPath path = pathOf();
        assertThat(path.getNodes().stream().map(PathNode::getSkillTag).toList())
                .containsExactly("oop", "java-language", "spring-boot");
        assertThat(path.getNodes().stream().map(PathNode::getStatus).toList())
                .containsExactly(NodeStatus.AVAILABLE, NodeStatus.LOCKED, NodeStatus.LOCKED);
    }

    @Test
    void completeNode_onALockedNode_isRejected() {
        declare(REST_AND_JWT);

        Result<LearningPath> result = complete(nodeId(pathOf(), "authentication-jwt"));

        assertThat(result.error().name()).isEqualTo("NODE_LOCKED");
    }
}
