package com.innovify.skillswap.learningpathengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.MiniProjectGenerationService;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** US19 (generation): real security, services and PostgreSQL; the AI provider is the test fake. */
class MiniProjectApiIntegrationTest extends PostgresIntegrationTest {

    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private MiniProjectGenerationService generation;

    private MockMvc mockMvc;
    private FakeMiniProjectGenerationService generator;
    private String anaToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        generator = (FakeMiniProjectGenerationService) generation;
        generator.reset();

        User ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        User bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
    }

    private MvcResult call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andReturn();
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

    /** Ana declares her goal; returns the id of the node of the skill. */
    private int declareGoalAndFindNode(String skill) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/learning-paths")
                .header("Authorization", "Bearer " + anaToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"" + GOAL + "\"}")).andReturn();
        assertThat(status(created)).as(body(created)).isEqualTo(201);
        List<Integer> ids = read(created, "$.nodes[?(@.skillTag=='" + skill + "')].id");
        return ids.get(0);
    }

    private static String url(int nodeId) {
        return "/api/v1/path-nodes/" + nodeId + "/mini-project";
    }

    @Test
    void generate_forAnAvailableNode_returns201WithTheStatementAndTheRubric() throws Exception {
        int nodeId = declareGoalAndFindNode("networking-basics");

        MvcResult result = call(post(url(nodeId)), anaToken);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat((int) read(result, "$.pathNodeId")).isEqualTo(nodeId);
        assertThat((String) read(result, "$.skillTag")).isEqualTo("networking-basics");
        assertThat((String) read(result, "$.title")).isNotBlank();
        assertThat((int) read(result, "$.passingScore")).isEqualTo(70);
        assertThat((List<?>) read(result, "$.rubric")).hasSize(4);
        assertThat((int) read(result, "$.rubric[0].weight")).isPositive();
        assertThat((String) read(result, "$.dueAt")).isNotBlank();
    }

    @Test
    void generate_again_returns200WithTheSameMiniProjectAndDoesNotCallTheProvider() throws Exception {
        int nodeId = declareGoalAndFindNode("networking-basics");
        int first = read(call(post(url(nodeId)), anaToken), "$.id");

        MvcResult second = call(post(url(nodeId)), anaToken);

        assertThat(status(second)).isEqualTo(200);
        assertThat((int) read(second, "$.id")).isEqualTo(first);
        assertThat(generator.requests()).hasSize(1);
    }

    @Test
    void generate_withRegenerate_returns201WithANewMiniProject() throws Exception {
        int nodeId = declareGoalAndFindNode("networking-basics");
        int first = read(call(post(url(nodeId)), anaToken), "$.id");

        MvcResult second = call(post(url(nodeId) + "?regenerate=true"), anaToken);

        assertThat(status(second)).isEqualTo(201);
        assertThat((int) read(second, "$.id")).isNotEqualTo(first);
        assertThat(generator.requests()).hasSize(2);
    }

    @Test
    void generate_forALockedNode_returns409() throws Exception {
        int nodeId = declareGoalAndFindNode("authentication-jwt");

        MvcResult result = call(post(url(nodeId)), anaToken);

        assertThat(status(result)).isEqualTo(409);
        assertThat((String) read(result, "$.title")).isEqualTo("NodeLocked");
    }

    @Test
    void generate_forAnotherStudentsNode_returns403() throws Exception {
        int nodeId = declareGoalAndFindNode("networking-basics");

        assertThat(status(call(post(url(nodeId)), bobToken))).isEqualTo(403);
    }

    @Test
    void generate_forAnUnknownNode_returns404() throws Exception {
        assertThat(status(call(post(url(9999)), anaToken))).isEqualTo(404);
    }

    @Test
    void generate_whenTheProviderFails_returns503() throws Exception {
        int nodeId = declareGoalAndFindNode("networking-basics");
        generator.failWith(new IllegalStateException("Gemini is down"));

        MvcResult result = call(post(url(nodeId)), anaToken);

        assertThat(status(result)).isEqualTo(503);
        generator.failWith(null);
        assertThat(status(call(get(url(nodeId)), anaToken))).isEqualTo(404);
    }

    @Test
    void generate_withoutToken_returns401() throws Exception {
        assertThat(status(call(post(url(1)), null))).isEqualTo(401);
    }

    @Test
    void get_returnsTheLatestMiniProject_andIs404ForOthersOrWhenThereIsNone() throws Exception {
        int nodeId = declareGoalAndFindNode("networking-basics");
        assertThat(status(call(get(url(nodeId)), anaToken))).isEqualTo(404);

        int id = read(call(post(url(nodeId)), anaToken), "$.id");

        MvcResult mine = call(get(url(nodeId)), anaToken);
        assertThat(status(mine)).isEqualTo(200);
        assertThat((int) read(mine, "$.id")).isEqualTo(id);
        assertThat(status(call(get(url(nodeId)), bobToken))).isEqualTo(404);
    }
}
