package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * T57 end to end: a student completes a skill with the real assessment flow, generates the public link and anyone
 * opens it without an account. Real security, services and PostgreSQL.
 */
class ShareableCredentialsApiIntegrationTest extends PostgresIntegrationTest {

    private static final String CREDENTIALS = "/api/v1/shareable-credentials";
    private static final String PUBLIC = "/api/v1/public/credentials/";
    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SKILL = "networking-basics";
    private static final String CREATE_BODY = "{\"skillTag\":\"" + SKILL + "\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private QuestionGenerationService questionGeneration;

    @Autowired
    private LearningPathContextFacade learningPath;

    @Autowired
    private WalletRepository wallets;

    private MockMvc mockMvc;
    private User ana;
    private User bob;
    private String anaToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        FakeQuestionGenerationService generator = (FakeQuestionGenerationService) questionGeneration;
        generator.failWith(null);
        generator.returnQuestionCount(5);
        generator.requests().clear();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
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

    /** The student declares the goal and passes the assessment of the skill, so the skill is verified. */
    private void verifySkill(User student, String token) throws Exception {
        assertThat(status(call(post("/api/v1/learning-paths"), token, "{\"goal\":\"" + GOAL + "\"}")))
                .isEqualTo(201);
        MvcResult path = call(get("/api/v1/learning-paths/" + student.getId()), token, null);
        List<Integer> nodeIds = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].id");
        MvcResult blueprint = call(post("/api/v1/path-nodes/" + nodeIds.get(0) + "/assessment-blueprint"), token, null);
        assertThat(status(blueprint)).isEqualTo(201);
        BlueprintView view = learningPath.getBlueprint((Integer) read(blueprint, "$.id")).orElseThrow();
        String answers = view.questions().stream().map(q -> String.valueOf(q.correctAnswer()))
                .collect(Collectors.joining(",", "[", "]"));
        MvcResult attempt = call(post("/api/v1/assessment-attempts"), token,
                "{\"blueprintId\":" + view.blueprintId() + ",\"selectedAnswers\":" + answers + "}");
        assertThat(status(attempt)).as(body(attempt)).isEqualTo(201);
    }

    private MvcResult create(String token) throws Exception {
        return call(post(CREDENTIALS), token, CREATE_BODY);
    }

    // ---------- Create ----------

    @Test
    void create_withoutToken_returns401() throws Exception {
        assertThat(status(call(post(CREDENTIALS), null, CREATE_BODY))).isEqualTo(401);
        assertThat(status(call(get(CREDENTIALS), null, null))).isEqualTo(401);
    }

    @Test
    void create_aSkillThatIsNotVerified_returns409() throws Exception {
        MvcResult result = create(anaToken);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("SkillNotVerified");
    }

    @Test
    void create_withoutSkill_returns400() throws Exception {
        MvcResult blank = call(post(CREDENTIALS), anaToken, "{\"skillTag\":\"  \"}");
        MvcResult missing = call(post(CREDENTIALS), anaToken, "{}");

        assertThat(status(blank)).isEqualTo(400);
        assertThat(title(blank)).isEqualTo("InvalidSkillTag");
        assertThat(status(missing)).isEqualTo(400);
        assertThat(title(missing)).isEqualTo("InvalidSkillTag");
    }

    @Test
    void create_aVerifiedSkill_returns201WithTheLinkAndTheText() throws Exception {
        verifySkill(ana, anaToken);
        wallets.save(new Wallet(ana.getId()).credit(new Credits(50)));

        MvcResult result = create(anaToken);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        String token = read(result, "$.token");
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat((String) read(result, "$.skillTag")).isEqualTo(SKILL);
        assertThat((String) read(result, "$.skillName")).isNotBlank();
        String url = read(result, "$.url");
        assertThat(url).endsWith("/api/v1/public/credentials/" + token);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(url);
        assertThat((String) read(result, "$.shareText")).contains("50 SkillCredits").contains(url);
        assertThat((String) read(result, "$.createdAt")).isNotBlank();
    }

    @Test
    void create_askingAgain_returns200WithTheSameLink() throws Exception {
        verifySkill(ana, anaToken);
        String token = read(create(anaToken), "$.token");

        MvcResult again = create(anaToken);

        assertThat(status(again)).isEqualTo(200);
        assertThat((String) read(again, "$.token")).isEqualTo(token);
        assertThat(queryString("SELECT count(*) FROM shareable_credentials")).isEqualTo("1");
    }

    @Test
    void create_theSkillOfAnotherStudent_returns409() throws Exception {
        verifySkill(ana, anaToken);

        assertThat(status(create(bobToken))).isEqualTo(409);
    }

    @Test
    void create_theShareTextFollowsTheLanguage() throws Exception {
        verifySkill(ana, anaToken);

        MvcResult result = mockMvc.perform(post(CREDENTIALS).header("Authorization", "Bearer " + anaToken)
                .header("Accept-Language", "es-419").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andReturn();

        assertThat(status(result)).isEqualTo(201);
        assertThat((String) read(result, "$.shareText")).startsWith("Verifiqué ");
    }

    // ---------- List ----------

    @Test
    void list_returnsOnlyTheCredentialsOfTheCaller() throws Exception {
        verifySkill(ana, anaToken);
        create(anaToken);

        MvcResult mine = call(get(CREDENTIALS), anaToken, null);
        MvcResult others = call(get(CREDENTIALS), bobToken, null);

        assertThat(status(mine)).isEqualTo(200);
        assertThat(read(mine, "$.length()").toString()).isEqualTo("1");
        assertThat((String) read(mine, "$[0].skillTag")).isEqualTo(SKILL);
        assertThat(status(others)).isEqualTo(200);
        assertThat(read(others, "$.length()").toString()).isEqualTo("0");
    }

    // ---------- Public page ----------

    @Test
    void publicPage_opensWithoutAnAccountAndHidesTheStudentData() throws Exception {
        verifySkill(ana, anaToken);
        wallets.save(new Wallet(ana.getId()).credit(new Credits(75)));
        String token = read(create(anaToken), "$.token");

        MvcResult result = call(get(PUBLIC + token), null, null);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.skillTag")).isEqualTo(SKILL);
        assertThat((String) read(result, "$.skillName")).isNotBlank();
        assertThat(read(result, "$.skillCredits").toString()).isEqualTo("75");
        assertThat((String) read(result, "$.issuedAt")).isNotBlank();
        assertThat(body(result)).doesNotContain("ana@upc.edu.pe").doesNotContain("ownerId")
                .doesNotContain("userId").doesNotContain("\"id\"");
    }

    @Test
    void publicPage_showsTheCurrentCredits() throws Exception {
        verifySkill(ana, anaToken);
        String token = read(create(anaToken), "$.token");
        assertThat(read(call(get(PUBLIC + token), null, null), "$.skillCredits").toString()).isEqualTo("0");

        wallets.save(new Wallet(ana.getId()).credit(new Credits(30)));

        assertThat(read(call(get(PUBLIC + token), null, null), "$.skillCredits").toString()).isEqualTo("30");
    }

    @Test
    void publicPage_ofAnUnknownToken_returns404() throws Exception {
        MvcResult result = call(get(PUBLIC + "x".repeat(43)), null, null);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("CredentialNotFound");
    }

    @Test
    void publicPage_otherMethodsAreNotPublic() throws Exception {
        assertThat(status(call(post(PUBLIC + "x".repeat(43)), null, null))).isIn(401, 403, 405);
    }
}
