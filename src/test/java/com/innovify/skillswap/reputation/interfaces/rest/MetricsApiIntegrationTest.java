package com.innovify.skillswap.reputation.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/**
 * T63 end to end: the platform metrics are for the Verificadores senior only. Real security, services, facades of the
 * other contexts and PostgreSQL.
 */
class MetricsApiIntegrationTest extends PostgresIntegrationTest {

    private static final String METRICS = "/api/v1/metrics";
    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private VerifierProfileRepository profileRepository;

    @Autowired
    private VerifierReliabilityRepository reliabilityRepository;

    private MockMvc mockMvc;
    private User ana;
    private User weak;
    private User senior;
    private String anaToken;
    private String weakToken;
    private String seniorToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        weak = userRepository.save(TestData.newUser("wendy", "wendy@upc.edu.pe", UserRole.STUDENT));
        senior = userRepository.save(TestData.newUser("sara", "sara@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        weakToken = tokenGenerator.generateToken(weak);
        seniorToken = tokenGenerator.generateToken(senior);

        profileRepository.save(new VerifierProfile(senior.getId(), "other-skill"));
        VerifierReliability reliability = new VerifierReliability(senior.getId());
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(calculator);
        }
        reliabilityRepository.save(reliability);

        VerifierProfile suspended = new VerifierProfile(weak.getId(), "networking-basics");
        suspended.suspend(senior.getId(), "Low reliability", Instant.now());
        profileRepository.save(suspended);
        reliabilityRepository.save(new VerifierReliability(weak.getId()));
        execute("UPDATE verifier_reliabilities SET score = 40 WHERE verifier_user_id = " + weak.getId());
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

    private MvcResult metric(String path, String token) throws Exception {
        return call(get(METRICS + path), token, null);
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

    // ---------- Access ----------

    @Test
    void everyMetric_withoutToken_returns401() throws Exception {
        for (String path : List.of("/demand", "/difficulty", "/verifiers")) {
            assertThat(status(metric(path, null))).as(path).isEqualTo(401);
        }
    }

    @Test
    void everyMetric_aVerifierThatIsNotSenior_returns403() throws Exception {
        for (String path : List.of("/demand", "/difficulty", "/verifiers")) {
            MvcResult result = metric(path, weakToken);
            assertThat(status(result)).as(path).isEqualTo(403);
            assertThat(title(result)).isEqualTo("NotSeniorVerifier");
        }
        assertThat(status(metric("/demand", anaToken))).isEqualTo(403);
    }

    // ---------- Demand ----------

    @Test
    void demand_withNoData_returnsEmptyListsAndTheDefaultPeriod() throws Exception {
        MvcResult result = metric("/demand", seniorToken);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(read(result, "$.to").toString()).isIn(today.toString(), today.minusDays(1).toString(),
                today.plusDays(1).toString());
        assertThat(read(result, "$.topSkills").getClass().getName()).contains("List");
        assertThat(read(result, "$.topCertifications").getClass().getName()).contains("List");
    }

    @Test
    void demand_countsTheGoalsDeclaredInThePeriod() throws Exception {
        assertThat(status(call(post("/api/v1/learning-paths"), anaToken, "{\"goal\":\"" + GOAL + "\"}")))
                .isEqualTo(201);

        MvcResult result = metric("/demand?limit=3", seniorToken);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(read(result, "$.topSkills.length()").toString()).isEqualTo("1");
        assertThat(read(result, "$.topSkills[0].pathCount").toString()).isEqualTo("1");
        assertThat((String) read(result, "$.topSkills[0].skillTag")).isNotBlank();
        assertThat((String) read(result, "$.topSkills[0].skillName")).isNotBlank();
    }

    @Test
    void demand_aPeriodInThePast_doesNotCountTodaysGoals() throws Exception {
        call(post("/api/v1/learning-paths"), anaToken, "{\"goal\":\"" + GOAL + "\"}");

        MvcResult result = metric("/demand?from=2020-01-01&to=2020-01-31", seniorToken);

        assertThat(status(result)).isEqualTo(200);
        assertThat(read(result, "$.topSkills.length()").toString()).isEqualTo("0");
        assertThat(read(result, "$.from").toString()).isEqualTo("2020-01-01");
        assertThat(read(result, "$.to").toString()).isEqualTo("2020-01-31");
    }

    @Test
    void demand_invalidParameters_return400() throws Exception {
        MvcResult badDate = metric("/demand?from=01-01-2020", seniorToken);
        assertThat(status(badDate)).isEqualTo(400);
        assertThat(title(badDate)).isEqualTo("InvalidPeriod");

        assertThat(title(metric("/demand?from=2026-02-01&to=2026-01-01", seniorToken))).isEqualTo("InvalidPeriod");
        assertThat(title(metric("/demand?from=2020-01-01&to=2026-01-01", seniorToken))).isEqualTo("InvalidPeriod");
        assertThat(title(metric("/demand?limit=0", seniorToken))).isEqualTo("InvalidLimit");
        assertThat(title(metric("/demand?limit=51", seniorToken))).isEqualTo("InvalidLimit");
    }

    @Test
    void demand_errorsAreLocalized() throws Exception {
        MvcResult result = mockMvc.perform(get(METRICS + "/demand?limit=0")
                .header("Authorization", "Bearer " + seniorToken)
                .header("Accept-Language", "es-419")).andReturn();

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.detail")).contains("límite");
    }

    // ---------- Difficulty ----------

    @Test
    void difficulty_withNoAttempts_returnsEmptyListsAndTheMinimumOfAttempts() throws Exception {
        MvcResult result = metric("/difficulty", seniorToken);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(read(result, "$.minAttempts").toString()).isEqualTo("5");
        assertThat(read(result, "$.skills.length()").toString()).isEqualTo("0");
        assertThat(read(result, "$.subTopics.length()").toString()).isEqualTo("0");
    }

    @Test
    void difficulty_invalidParameters_return400() throws Exception {
        assertThat(title(metric("/difficulty?to=nope", seniorToken))).isEqualTo("InvalidPeriod");
        assertThat(title(metric("/difficulty?limit=99", seniorToken))).isEqualTo("InvalidLimit");
    }

    // ---------- Verifiers ----------

    @Test
    void verifiers_areListedHighestReliabilityFirst() throws Exception {
        MvcResult result = metric("/verifiers", seniorToken);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(read(result, "$.verifiers.length()").toString()).isEqualTo("2");
        assertThat(read(result, "$.verifiers[0].verifierUserId").toString()).isEqualTo(String.valueOf(senior.getId()));
        assertThat(read(result, "$.verifiers[0].score").toString()).isEqualTo("100");
        assertThat((String) read(result, "$.verifiers[0].rank")).isEqualTo("Gold");
        assertThat((Boolean) read(result, "$.verifiers[0].seniorVerifier")).isTrue();
        assertThat((Boolean) read(result, "$.verifiers[0].suspended")).isFalse();
        assertThat(read(result, "$.verifiers[1].verifierUserId").toString()).isEqualTo(String.valueOf(weak.getId()));
        assertThat(read(result, "$.verifiers[1].score").toString()).isEqualTo("40");
        assertThat((Boolean) read(result, "$.verifiers[1].suspended")).isTrue();
        assertThat((Object) read(result, "$.verifiers[1].averageResolutionHours")).isNull();
    }

    @Test
    void verifiers_ascReversesTheOrder() throws Exception {
        MvcResult result = metric("/verifiers?order=asc", seniorToken);

        assertThat(status(result)).isEqualTo(200);
        assertThat(read(result, "$.verifiers[0].verifierUserId").toString()).isEqualTo(String.valueOf(weak.getId()));
    }

    @Test
    void verifiers_unknownOrder_returns400() throws Exception {
        MvcResult result = metric("/verifiers?order=sideways", seniorToken);

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidOrder");
    }
}
