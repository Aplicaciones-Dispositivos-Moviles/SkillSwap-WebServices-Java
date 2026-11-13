package com.innovify.skillswap.learningpathengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.FakeGeminiServer;
import com.innovify.skillswap.learningpathengine.infrastructure.ai.FakeGeminiServer.Reply;
import com.innovify.skillswap.shared.infrastructure.json.Json;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US06 end to end: POST /api/v1/learning-paths with the hybrid interpretation enabled, against a fake Gemini
 * server on the loopback interface (no network, no real key) and PostgreSQL. Its own Spring context, because the
 * other integration tests interpret the goals by keywords only.
 */
class GoalInterpretationApiIntegrationTest extends PostgresIntegrationTest {

    private static final FakeGeminiServer GEMINI = FakeGeminiServer.start();
    private static final String REST_AND_JWT = "quiero aprender a construir APIs REST con autenticación JWT";

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("gemini.goal-interpretation.enabled", () -> "true");
        registry.add("gemini.goal-interpretation.timeout-seconds", () -> "5");
        registry.add("gemini.base-url", GEMINI::baseUrl);
        registry.add("gemini.max-retries", () -> "0");
        registry.add("gemini.retry-delay-milliseconds", () -> "0");
    }

    @AfterAll
    static void stopGemini() {
        GEMINI.close();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    private MockMvc mockMvc;
    private String anaToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        GEMINI.requests().clear();
    }

    private MvcResult declare(String goal) throws Exception {
        return mockMvc.perform(post("/api/v1/learning-paths").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("goal", goal)))
                        .header("Authorization", "Bearer " + anaToken))
                .andReturn();
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    @Test
    @DisplayName("US06 escenario 1: Gemini interprets the goal and only catalog skills reach the path")
    void declare_usesTheCatalogSkillsGeminiSelectedAndDiscardsTheRest() throws Exception {
        GEMINI.respond(Reply.json(Map.of("skillTags", List.of("authentication-jwt", "oauth-for-cats"))));

        MvcResult result = declare(REST_AND_JWT);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(this.<List<String>>read(result, "$.goalSkillTags")).containsExactly("authentication-jwt");
        assertThat(this.<List<String>>read(result, "$.nodes[*].skillTag")).containsExactly("networking-basics",
                "programming-fundamentals", "http-basics", "rest-api-design", "authentication-jwt");
        assertThat(GEMINI.requests()).hasSize(1);
        assertThat(GEMINI.requests().get(0).apiKey()).isEqualTo("test-gemini-key");
        assertThat(GEMINI.requests().get(0).prompt()).contains(REST_AND_JWT);
    }

    @Test
    void declare_whenGeminiUnderstandsWhatTheKeywordsDoNot_createsThePath() throws Exception {
        GEMINI.respond(Reply.json(Map.of("skillTags", List.of("sql-fundamentals"))));

        MvcResult result = declare("quiero guardar la información de mi tienda en tablas");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(this.<List<String>>read(result, "$.goalSkillTags")).containsExactly("sql-fundamentals");
    }

    @Test
    @DisplayName("US06 escenario 3: when Gemini fails the keywords of the catalog build the path")
    void declare_whenGeminiFails_usesTheKeywordMatcher() throws Exception {
        GEMINI.respond(Reply.status(503));

        MvcResult result = declare(REST_AND_JWT);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(this.<List<String>>read(result, "$.goalSkillTags"))
                .containsExactlyInAnyOrder("rest-api-design", "authentication-jwt");
        assertThat(GEMINI.requests()).isNotEmpty();
    }

    @Test
    void declare_whenGeminiSelectsNoCatalogSkill_usesTheKeywordMatcher() throws Exception {
        GEMINI.respond(Reply.json(Map.of("skillTags", List.of("made-up-skill"))));

        MvcResult result = declare(REST_AND_JWT);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(this.<List<String>>read(result, "$.goalSkillTags"))
                .containsExactlyInAnyOrder("rest-api-design", "authentication-jwt");
    }

    @Test
    @DisplayName("US06 escenario 2: neither Gemini nor the keywords find a skill: 422")
    void declare_whenNothingCanBeInterpreted_returns422() throws Exception {
        GEMINI.respond(Reply.json(Map.of("skillTags", List.of())));

        MvcResult result = declare("quiero aprender a cocinar pasteles");

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(this.<String>read(result, "$.title")).isEqualTo("GoalNotInterpretable");
        assertThat(queryString("SELECT count(*) FROM learning_paths")).isEqualTo("0");
    }
}
