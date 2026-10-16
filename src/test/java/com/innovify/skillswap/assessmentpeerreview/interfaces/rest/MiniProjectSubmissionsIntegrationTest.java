package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.repositories.MiniProjectRepository;
import com.innovify.skillswap.learningpathengine.domain.services.MiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US19 end to end: the student generates the mini-project of a node and delivers its repository. Real security,
 * services and PostgreSQL; the AI providers are the test fakes.
 */
class MiniProjectSubmissionsIntegrationTest extends PostgresIntegrationTest {

    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SKILL = "networking-basics";
    private static final String REPOSITORY = "https://github.com/ana/networking-basics";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private MiniProjectGenerationService miniProjectGeneration;

    @Autowired
    private QuestionGenerationService questionGeneration;

    @Autowired
    private LearningPathContextFacade learningPath;

    @Autowired
    private MiniProjectRepository miniProjects;

    @Autowired
    private MiniProjectSubmissionRepository submissions;

    private MockMvc mockMvc;
    private String anaToken;
    private String bobToken;
    private int nodeId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ((FakeMiniProjectGenerationService) miniProjectGeneration).reset();
        FakeQuestionGenerationService questions = (FakeQuestionGenerationService) questionGeneration;
        questions.failWith(null);
        questions.returnQuestionCount(5);
        questions.requests().clear();

        User ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        User bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);

        MvcResult path = call(post("/api/v1/learning-paths"), anaToken, "{\"goal\":\"" + GOAL + "\"}");
        assertThat(status(path)).as(body(path)).isEqualTo(201);
        List<Integer> nodeIds = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].id");
        nodeId = nodeIds.get(0);
    }

    // ---------- Helpers ----------

    private MvcResult call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
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

    /** Ana generates the mini-project of the node; returns its id. */
    private int generateMiniProject(boolean regenerate) throws Exception {
        MvcResult result = call(post("/api/v1/path-nodes/" + nodeId + "/mini-project" + (regenerate ? "?regenerate=true" : "")),
                anaToken, null);
        assertThat(status(result)).as(body(result)).isIn(200, 201);
        return read(result, "$.id");
    }

    private MvcResult submit(String token, int miniProjectId, String url) throws Exception {
        String json = "{\"repositoryUrl\":" + (url == null ? "null" : "\"" + url + "\"") + "}";
        return call(post("/api/v1/mini-projects/" + miniProjectId + "/submissions"), token, json);
    }

    /** Ana fails the assessment of the node, which opens a case for it. */
    private void failAssessment() throws Exception {
        MvcResult blueprint = call(post("/api/v1/path-nodes/" + nodeId + "/assessment-blueprint"), anaToken, null);
        assertThat(status(blueprint)).as(body(blueprint)).isEqualTo(201);
        BlueprintView view = learningPath.getBlueprint(read(blueprint, "$.id")).orElseThrow();
        String answers = view.questions().stream().map(q -> String.valueOf((q.correctAnswer() + 1) % 4))
                .collect(Collectors.joining(",", "[", "]"));
        MvcResult attempt = call(post("/api/v1/assessment-attempts"), anaToken,
                "{\"blueprintId\":" + view.blueprintId() + ",\"selectedAnswers\":" + answers + "}");
        assertThat(status(attempt)).as(body(attempt)).isEqualTo(201);
    }

    // ---------- Tests ----------

    @Test
    void submit_returns201WithTheDeliveryAndSavesIt() throws Exception {
        int miniProjectId = generateMiniProject(false);

        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat((int) read(result, "$.miniProjectId")).isEqualTo(miniProjectId);
        assertThat((int) read(result, "$.pathNodeId")).isEqualTo(nodeId);
        assertThat((String) read(result, "$.skillTag")).isEqualTo(SKILL);
        assertThat((String) read(result, "$.repositoryUrl")).isEqualTo(REPOSITORY);
        assertThat((String) read(result, "$.submittedAt")).isNotBlank();
        assertThat(submissions.findByMiniProjectId(miniProjectId)).isPresent();
    }

    @Test
    void submit_twice_returns409AndKeepsTheFirstDelivery() throws Exception {
        int miniProjectId = generateMiniProject(false);
        assertThat(status(submit(anaToken, miniProjectId, REPOSITORY))).isEqualTo(201);

        MvcResult second = submit(anaToken, miniProjectId, "https://github.com/ana/other");

        assertThat(status(second)).isEqualTo(409);
        assertThat(title(second)).isEqualTo("MiniProjectAlreadySubmitted");
        assertThat(submissions.findByMiniProjectId(miniProjectId).orElseThrow().getRepositoryUrl())
                .isEqualTo(REPOSITORY);
    }

    @Test
    void submit_anotherStudentsMiniProject_returns403() throws Exception {
        int miniProjectId = generateMiniProject(false);

        MvcResult result = submit(bobToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotMiniProjectOwner");
        assertThat(submissions.findByMiniProjectId(miniProjectId)).isEmpty();
    }

    @Test
    void submit_anUnknownMiniProject_returns404() throws Exception {
        MvcResult result = submit(anaToken, 9999, REPOSITORY);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("MiniProjectNotFound");
    }

    @Test
    void submit_withAnInvalidLink_returns400() throws Exception {
        int miniProjectId = generateMiniProject(false);

        for (String url : new String[]{null, "", "github.com/ana/repo", "ftp://github.com/ana/repo"}) {
            MvcResult result = submit(anaToken, miniProjectId, url);
            assertThat(status(result)).as("url=" + url).isEqualTo(400);
            assertThat(title(result)).isEqualTo("InvalidRepositoryUrl");
        }
        assertThat(submissions.findByMiniProjectId(miniProjectId)).isEmpty();
    }

    @Test
    void submit_afterTheDeadline_returns409() throws Exception {
        int miniProjectId = generateMiniProject(false);
        MiniProject miniProject = miniProjects.findById(miniProjectId).orElseThrow();
        ReflectionTestUtils.setField(miniProject, "dueAt", Instant.now().minusSeconds(60));
        miniProjects.save(miniProject);

        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("MiniProjectExpired");
    }

    @Test
    void submit_anOlderMiniProject_returns409_andTheRegeneratedOneIsAccepted() throws Exception {
        int first = generateMiniProject(false);
        int second = generateMiniProject(true);

        MvcResult outdated = submit(anaToken, first, REPOSITORY);
        MvcResult latest = submit(anaToken, second, REPOSITORY);

        assertThat(status(outdated)).isEqualTo(409);
        assertThat(title(outdated)).isEqualTo("MiniProjectOutdated");
        assertThat(status(latest)).as(body(latest)).isEqualTo(201);
    }

    @Test
    void submit_whenTheNodeHasACaseInProgress_returns409() throws Exception {
        int miniProjectId = generateMiniProject(false);
        failAssessment();

        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("OpenCaseAlreadyExists");
        assertThat(submissions.findByMiniProjectId(miniProjectId)).isEmpty();
    }

    @Test
    void submit_withoutToken_returns401() throws Exception {
        assertThat(status(submit(null, 1, REPOSITORY))).isEqualTo(401);
    }

    @Test
    void getMiniProject_isStillTheOneThatWasDelivered() throws Exception {
        int miniProjectId = generateMiniProject(false);
        submit(anaToken, miniProjectId, REPOSITORY);

        MvcResult result = call(get("/api/v1/path-nodes/" + nodeId + "/mini-project"), anaToken, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((int) read(result, "$.id")).isEqualTo(miniProjectId);
    }
}
