package com.innovify.skillswap.learningpathengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.shared.infrastructure.json.Json;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The learning path API end to end: real security filter, JWT, services, skill catalog and PostgreSQL, with the
 * AI replaced by a fake. It also holds the scenarios of the three C# feature files (LearningPathGoal,
 * LearningPathConsultation and AssessmentGeneration).
 */
class LearningPathApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PATHS = "/api/v1/learning-paths";
    private static final String REST_AND_JWT = "quiero aprender a construir APIs REST con autenticación JWT";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private QuestionGenerationService questionGeneration;

    @Autowired
    private AssessmentBlueprintRepository blueprints;

    private MockMvc mockMvc;
    private FakeQuestionGenerationService generator;
    private User ana;
    private User bob;
    private String anaToken;
    private String bobToken;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        generator = (FakeQuestionGenerationService) questionGeneration;
        generator.failWith(null);
        generator.returnQuestionCount(5);
        generator.requests().clear();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
    }

    // ---------- Helpers ----------

    private MvcResult declare(String token, String goal) throws Exception {
        return declareJson(token, Json.write(Map.of("goal", goal)));
    }

    private MvcResult declareJson(String token, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(PATHS).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult declareOk(String token, String goal) throws Exception {
        MvcResult result = declare(token, goal);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return result;
    }

    private MvcResult getPath(String token, int studentId) throws Exception {
        MockHttpServletRequestBuilder request = get(PATHS + "/" + studentId);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult requestBlueprint(String token, int nodeId) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/path-nodes/" + nodeId + "/assessment-blueprint");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private static String title(MvcResult result) throws Exception {
        return read(result, "$.title");
    }

    private static List<String> skills(MvcResult path) throws Exception {
        return read(path, "$.nodes[*].skillTag");
    }

    private static List<String> statuses(MvcResult path) throws Exception {
        return read(path, "$.nodes[*].status");
    }

    private static int nodeId(MvcResult path, String skillTag) throws Exception {
        List<Integer> ids = read(path, "$.nodes[?(@.skillTag=='" + skillTag + "')].id");
        return ids.get(0);
    }

    private static Map<String, Object> node(MvcResult path, String skillTag) throws Exception {
        List<Map<String, Object>> nodes = read(path, "$.nodes[?(@.skillTag=='" + skillTag + "')]");
        return nodes.get(0);
    }

    private static List<Map<String, Object>> linkedNodes(MvcResult path) throws Exception {
        List<Map<String, Object>> nodes = read(path, "$.nodes");
        return nodes.stream().filter(n -> n.get("linkedCertificateId") != null).toList();
    }

    private int uploadCertificate(String token, String courseName) throws Exception {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] seed = "cert".getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[header.length + seed.length];
        System.arraycopy(header, 0, file, 0, header.length);
        System.arraycopy(seed, 0, file, header.length, seed.length);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/certificates")
                        .file(new MockMultipartFile("file", "certificate", "image/jpeg", file))
                        .param("courseName", courseName)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(status(upload)).isEqualTo(201);
        return read(upload, "$.id");
    }

    // ---------- Declare goal ----------

    @Test
    @DisplayName("Declare an interpretable goal")
    void declare_withAnInterpretableGoal_returns201WithTheOrderedNodes() throws Exception {
        MvcResult result = declare(anaToken, REST_AND_JWT);

        assertThat(status(result)).isEqualTo(201);
        assertThat(this.<Integer>read(result, "$.studentId")).isEqualTo(ana.getId());
        assertThat(this.<String>read(result, "$.goal")).isEqualTo(REST_AND_JWT);
        assertThat(this.<String>read(result, "$.status")).isEqualTo("Active");
        assertThat(this.<List<String>>read(result, "$.goalSkillTags"))
                .containsExactlyInAnyOrder("authentication-jwt", "rest-api-design");
        assertThat(skills(result)).containsExactly(
                "networking-basics", "programming-fundamentals", "http-basics", "rest-api-design",
                "authentication-jwt");
        assertThat(statuses(result)).containsExactly("Available", "Available", "Locked", "Locked", "Locked");
        assertThat(node(result, "rest-api-design")).containsEntry("skillName", "REST API design");
        assertThat(node(result, "rest-api-design").get("prerequisiteSkillTags")).asList()
                .containsExactly("http-basics", "programming-fundamentals");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(PATHS + "/" + ana.getId());
    }

    @Test
    void declare_ignoresAStudentIdSentInTheBody() throws Exception {
        MvcResult result = declareJson(anaToken,
                "{\"goal\": \"" + REST_AND_JWT + "\", \"studentId\": 999}");

        assertThat(status(result)).isEqualTo(201);
        assertThat(this.<Integer>read(result, "$.studentId")).isEqualTo(ana.getId());
    }

    @Test
    @DisplayName("Reject a goal that matches no skill")
    void declare_withAGoalThatMatchesNoSkill_returns422() throws Exception {
        MvcResult result = declare(anaToken, "quiero cocinar pasteles");

        assertThat(status(result)).isEqualTo(422);
        assertThat(title(result)).isEqualTo("GoalNotInterpretable");
        assertThat(this.<String>read(result, "$.detail")).isEqualTo("We could not match your goal with any skill. "
                + "Try describing it with more specific terms, such as a technology or a role.");
    }

    @DisplayName("Reject a blank goal")
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void declare_withABlankGoal_returns400(String goal) throws Exception {
        MvcResult result = declare(anaToken, goal);

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidGoal");
    }

    @Test
    void declare_withoutAGoalProperty_returns400() throws Exception {
        MvcResult result = declareJson(anaToken, "{}");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidGoal");
    }

    @Test
    void declare_withAGoalOver500Characters_returns400() throws Exception {
        MvcResult result = declare(anaToken, "a".repeat(501));

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidGoal");
    }

    @Test
    @DisplayName("A student cannot have two active paths")
    void declare_whenThereIsAnActivePath_returns409() throws Exception {
        declareOk(anaToken, REST_AND_JWT);

        MvcResult result = declare(anaToken, "quiero aprender SQL");

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("ActivePathAlreadyExists");
    }

    @Test
    void declare_withoutToken_returns401() throws Exception {
        assertThat(status(declare(null, REST_AND_JWT))).isEqualTo(401);
    }

    @Test
    void declare_errorMessagesFollowTheAcceptLanguageHeader() throws Exception {
        MvcResult result = mockMvc.perform(post(PATHS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("goal", "quiero cocinar pasteles")))
                        .header("Accept-Language", "es-PE")
                        .header("Authorization", "Bearer " + anaToken))
                .andReturn();

        assertThat(this.<String>read(result, "$.detail")).startsWith("No pudimos relacionar tu meta");
    }

    @Test
    void declare_isPersistedWithTheTextTheCSharpApiWrites() throws Exception {
        declareOk(anaToken, REST_AND_JWT);

        assertThat(queryString("SELECT status FROM learning_paths")).isEqualTo("Active");
        assertThat(queryString("SELECT status FROM path_nodes ORDER BY node_order LIMIT 1")).isEqualTo("Available");
    }

    // ---------- Get path ----------

    @Test
    @DisplayName("A student consults their active path")
    void get_asTheOwner_returns200WithTheNodesAndTheirStates() throws Exception {
        MvcResult declared = declareOk(anaToken, "quiero aprender HTTP");

        MvcResult result = getPath(anaToken, ana.getId());

        assertThat(status(result)).isEqualTo(200);
        assertThat(this.<Integer>read(result, "$.id")).isEqualTo(this.<Integer>read(declared, "$.id"));
        assertThat(skills(result)).containsExactly("networking-basics", "http-basics");
        assertThat(statuses(result)).containsExactly("Available", "Locked");
    }

    @Test
    @DisplayName("A student without a path gets not found")
    void get_forAStudentWithoutPath_returns404() throws Exception {
        MvcResult result = getPath(anaToken, ana.getId());

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("PathNotFound");
    }

    @Test
    @DisplayName("A student cannot consult another student's path")
    void get_asAnotherStudent_returns403() throws Exception {
        declareOk(anaToken, REST_AND_JWT);

        MvcResult result = getPath(bobToken, ana.getId());

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotPathOwner");
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        assertThat(status(getPath(null, 1))).isEqualTo(401);
    }

    // ---------- Certificates ----------

    @Test
    void get_asTheOwner_linksCertificatesUploadedAfterTheGoal_andAnotherStudentsReadNeverWrites() throws Exception {
        declareOk(anaToken, REST_AND_JWT);
        int certificateId = uploadCertificate(anaToken, "REST API fundamentals");

        assertThat(status(getPath(bobToken, ana.getId()))).isEqualTo(403);
        assertThat(queryString("SELECT count(*) FROM path_nodes WHERE linked_certificate_id IS NOT NULL"))
                .isEqualTo("0");

        MvcResult byOwner = getPath(anaToken, ana.getId());
        assertThat(linkedNodes(byOwner)).hasSize(1);
        assertThat(node(byOwner, "rest-api-design")).containsEntry("linkedCertificateId", certificateId);
        assertThat(node(byOwner, "rest-api-design")).containsEntry("status", "Locked");

        // The link was saved: a later read sees it too.
        MvcResult again = getPath(anaToken, ana.getId());
        assertThat(node(again, "rest-api-design")).containsEntry("linkedCertificateId", certificateId);
    }

    @Test
    void declare_linksTheCertificatesTheStudentAlreadyHad() throws Exception {
        int certificateId = uploadCertificate(anaToken, "REST API fundamentals");

        MvcResult path = declareOk(anaToken, REST_AND_JWT);

        assertThat(node(path, "rest-api-design")).containsEntry("linkedCertificateId", certificateId);
    }

    // ---------- Generate assessment ----------

    @Test
    @DisplayName("Generate the assessment of an available node")
    void generate_forAnAvailableNode_returns201WithFiveQuestionsAndNoCorrectAnswers() throws Exception {
        int nodeId = nodeId(declareOk(anaToken, REST_AND_JWT), "networking-basics");

        MvcResult result = requestBlueprint(anaToken, nodeId);

        assertThat(status(result)).isEqualTo(201);
        assertThat(body(result).toLowerCase()).doesNotContain("correct");
        assertThat(this.<Integer>read(result, "$.pathNodeId")).isEqualTo(nodeId);
        assertThat(this.<String>read(result, "$.skillTag")).isEqualTo("networking-basics");
        assertThat(this.<String>read(result, "$.skillName")).isEqualTo("Networking basics");
        assertThat(this.<List<Object>>read(result, "$.questions")).hasSize(5);
        assertThat(this.<List<List<String>>>read(result, "$.questions[*].answers"))
                .allSatisfy(answers -> assertThat(answers).hasSize(4));
    }

    @Test
    void generate_pointsTheNodeToTheBlueprintAndKeepsTheCorrectAnswersOnTheServer() throws Exception {
        int nodeId = nodeId(declareOk(anaToken, REST_AND_JWT), "networking-basics");

        MvcResult blueprint = requestBlueprint(anaToken, nodeId);

        MvcResult path = getPath(anaToken, ana.getId());
        assertThat(node(path, "networking-basics")).containsEntry("assessmentBlueprintId",
                this.<Integer>read(blueprint, "$.id"));

        var stored = blueprints.findLatestByPathNodeId(nodeId).orElseThrow();
        assertThat(stored.getQuestions()).hasSize(5);
        assertThat(stored.getQuestions()).allSatisfy(question ->
                assertThat(question.getCorrectAnswer()).isBetween(0, Question.ANSWER_COUNT - 1));
    }

    @Test
    @DisplayName("A new attempt generates different questions")
    void generate_again_returnsDifferentQuestionsAndPointsToTheNewBlueprint() throws Exception {
        int nodeId = nodeId(declareOk(anaToken, REST_AND_JWT), "networking-basics");

        MvcResult first = requestBlueprint(anaToken, nodeId);
        MvcResult second = requestBlueprint(anaToken, nodeId);

        assertThat(status(second)).isEqualTo(201);
        assertThat(this.<Integer>read(first, "$.id")).isNotEqualTo(this.<Integer>read(second, "$.id"));
        assertThat(this.<List<String>>read(first, "$.questions[*].question"))
                .doesNotContainAnyElementsOf(this.<List<String>>read(second, "$.questions[*].question"));
        assertThat(node(getPath(anaToken, ana.getId()), "networking-basics"))
                .containsEntry("assessmentBlueprintId", this.<Integer>read(second, "$.id"));
    }

    @Test
    @DisplayName("Reject the assessment of a locked node")
    void generate_forALockedNode_returns409ListingThePendingPrerequisites() throws Exception {
        int nodeId = nodeId(declareOk(anaToken, REST_AND_JWT), "rest-api-design");

        MvcResult result = requestBlueprint(anaToken, nodeId);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("NodeLocked");
        assertThat(this.<List<String>>read(result, "$.pendingPrerequisites"))
                .containsExactly("http-basics", "programming-fundamentals");
        assertThat(generator.requests()).isEmpty();
    }

    @Test
    @DisplayName("A student cannot request the assessment of another student's node")
    void generate_forAnotherStudentsNode_returns403() throws Exception {
        int nodeId = nodeId(declareOk(anaToken, REST_AND_JWT), "networking-basics");

        MvcResult result = requestBlueprint(bobToken, nodeId);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotPathOwner");
        assertThat(generator.requests()).isEmpty();
    }

    @Test
    void generate_forAnUnknownNode_returns404() throws Exception {
        MvcResult result = requestBlueprint(anaToken, 9999);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("NodeNotFound");
    }

    @Test
    void generate_withoutToken_returns401() throws Exception {
        assertThat(status(requestBlueprint(null, 1))).isEqualTo(401);
    }

    @Test
    @DisplayName("The node keeps its state when the AI service is unavailable")
    void generate_whenTheAiServiceFails_returns503AndLeavesTheNodeUnchanged() throws Exception {
        int nodeId = nodeId(declareOk(anaToken, REST_AND_JWT), "networking-basics");
        generator.failWith(new IllegalStateException("AI service unavailable"));

        MvcResult result = requestBlueprint(anaToken, nodeId);

        assertThat(status(result)).isEqualTo(503);
        assertThat(title(result)).isEqualTo("QuestionGenerationFailed");

        Map<String, Object> node = node(getPath(anaToken, ana.getId()), "networking-basics");
        assertThat(node).containsEntry("status", "Available");
        assertThat(node.get("assessmentBlueprintId")).isNull();
    }

    @Test
    void generate_whenTheAiReturnsTheWrongNumberOfQuestions_returns503() throws Exception {
        int nodeId = nodeId(declareOk(anaToken, REST_AND_JWT), "networking-basics");
        generator.returnQuestionCount(4);

        MvcResult result = requestBlueprint(anaToken, nodeId);

        assertThat(status(result)).isEqualTo(503);
    }
}
