package com.innovify.skillswap.reputation.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.AssessmentAttemptPassed;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
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
 * The Reputation API end to end, fed by the real events of Assessment &amp; Peer Review. Reading a reputation is
 * private: only its owner can.
 */
class ReputationApiIntegrationTest extends PostgresIntegrationTest {

    private static final String ATTEMPTS = "/api/v1/assessment-attempts";
    private static final String CASES = "/api/v1/verification-cases";
    private static final String EMPLOYABILITY = "/api/v1/student-employability-scores/";
    private static final String RELIABILITY = "/api/v1/verifier-reliabilities/";
    private static final String PROFILES = "/api/v1/verifier-profiles";
    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SKILL = "networking-basics";
    private static final String EVIDENCE = "https://github.com/ana/project";

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
    private DomainEventPublisher publisher;

    private MockMvc mockMvc;
    private User ana;
    private User bob;
    private User carla;
    private User dan;
    private String anaToken;
    private String bobToken;
    private String carlaToken;
    private String danToken;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        FakeQuestionGenerationService generator = (FakeQuestionGenerationService) questionGeneration;
        generator.failWith(null);
        generator.returnQuestionCount(5);
        generator.requests().clear();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        carla = userRepository.save(TestData.newUser("carla", "carla@upc.edu.pe", UserRole.STUDENT));
        dan = userRepository.save(TestData.newUser("dan", "dan@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
        carlaToken = tokenGenerator.generateToken(carla);
        danToken = tokenGenerator.generateToken(dan);
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

    private static String json(List<Integer> answers) {
        return answers.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    /** The student declares the goal (once) and asks for the assessment of the skill. */
    private BlueprintView newBlueprint(User student, String token) throws Exception {
        MvcResult path = call(get("/api/v1/learning-paths/" + student.getId()), token, null);
        if (status(path) == 404) {
            assertThat(status(call(post("/api/v1/learning-paths"), token,
                    "{\"goal\":\"" + GOAL + "\"}"))).isEqualTo(201);
            path = call(get("/api/v1/learning-paths/" + student.getId()), token, null);
        }
        List<Integer> nodeIds = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].id");
        MvcResult blueprint = call(post("/api/v1/path-nodes/" + nodeIds.get(0) + "/assessment-blueprint"), token, null);
        assertThat(status(blueprint)).isEqualTo(201);
        int blueprintId = read(blueprint, "$.id");
        return learningPath.getBlueprint(blueprintId).orElseThrow();
    }

    private static List<Integer> passing(BlueprintView blueprint) {
        return blueprint.questions().stream().map(q -> q.correctAnswer()).toList();
    }

    private static List<Integer> failing(BlueprintView blueprint) {
        return blueprint.questions().stream().map(q -> (q.correctAnswer() + 1) % 4).toList();
    }

    private MvcResult submit(String token, int blueprintId, List<Integer> answers) throws Exception {
        return call(post(ATTEMPTS), token,
                "{\"blueprintId\":" + blueprintId + ",\"selectedAnswers\":" + json(answers) + "}");
    }

    private MvcResult passAssessment(User student, String token) throws Exception {
        BlueprintView blueprint = newBlueprint(student, token);
        MvcResult result = submit(token, blueprint.blueprintId(), passing(blueprint));
        assertThat(status(result)).isEqualTo(201);
        return result;
    }

    /** Fails the assessment and returns the id of the case that was opened. */
    private int failAssessment(User student, String token) throws Exception {
        BlueprintView blueprint = newBlueprint(student, token);
        MvcResult result = submit(token, blueprint.blueprintId(), failing(blueprint));
        assertThat(status(result)).isEqualTo(201);
        return read(result, "$.verificationCaseId");
    }

    /** The student completes the skill and becomes a verifier of it. */
    private void enroll(User student, String token) throws Exception {
        passAssessment(student, token);
        MvcResult created = call(post(PROFILES), token, "{\"skillTag\":\"" + SKILL + "\"}");
        assertThat(status(created)).as(body(created)).isEqualTo(201);
    }

    private MvcResult getCase(String token, int caseId) throws Exception {
        return call(get(CASES + "/" + caseId), token, null);
    }

    private MvcResult decide(String token, int caseId, String decision, String notes) throws Exception {
        return call(patch(CASES + "/" + caseId + "/decision"), token,
                "{\"decision\":\"" + decision + "\",\"rubricNotes\":\"" + notes + "\"}");
    }

    private MvcResult appeal(String token, int caseId) throws Exception {
        return call(post(CASES + "/" + caseId + "/appeal"), token, null);
    }

    private MvcResult employability(String token, User student) throws Exception {
        return call(get(EMPLOYABILITY + student.getId()), token, null);
    }

    private MvcResult reliability(String token, User verifier) throws Exception {
        return call(get(RELIABILITY + verifier.getId()), token, null);
    }

    private double profileRating(String token) throws Exception {
        return read(call(get(PROFILES + "/me"), token, null), "$.rating");
    }

    // ---------- Employability ----------

    @Test
    void employability_afterPassingAnAssessment_showsOneSkillAndScore10() throws Exception {
        passAssessment(ana, anaToken);

        MvcResult result = employability(anaToken, ana);

        assertThat(status(result)).isEqualTo(200);
        assertThat((int) read(result, "$.studentId")).isEqualTo(ana.getId());
        assertThat((int) read(result, "$.verifiedSkillsCount")).isEqualTo(1);
        assertThat((int) read(result, "$.score")).isEqualTo(10);
    }

    @Test
    void employability_withTwoSkills_showsScore20() throws Exception {
        passAssessment(ana, anaToken);
        publisher.publish(new AssessmentAttemptPassed(999, ana.getId(), 999, "sql-joins"));

        MvcResult result = employability(anaToken, ana);

        assertThat((int) read(result, "$.verifiedSkillsCount")).isEqualTo(2);
        assertThat((int) read(result, "$.score")).isEqualTo(20);
    }

    @Test
    void employability_withoutCertifiedSkills_returns404() throws Exception {
        MvcResult result = employability(anaToken, ana);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("ReputationNotFound");
    }

    @Test
    void employability_afterAVerifierApproves_certifiesTheStudent() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        assertThat(status(decide(bobToken, caseId, "Approved", "Buen trabajo"))).isEqualTo(200);

        MvcResult result = employability(anaToken, ana);
        assertThat(status(result)).isEqualTo(200);
        assertThat((int) read(result, "$.verifiedSkillsCount")).isEqualTo(1);
        assertThat((int) read(result, "$.score")).isEqualTo(10);
    }

    @Test
    void employability_afterAVerifierRejects_doesNotCertifyTheStudent() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        assertThat(status(decide(bobToken, caseId, "Rejected", "Falta evidencia"))).isEqualTo(200);

        assertThat(status(employability(anaToken, ana))).isEqualTo(404);
    }

    @Test
    void employability_ofAnotherStudent_returns403() throws Exception {
        passAssessment(ana, anaToken);

        MvcResult result = employability(bobToken, ana);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotReputationOwner");
    }

    @Test
    void employability_withoutToken_returns401() throws Exception {
        assertThat(status(employability(null, ana))).isEqualTo(401);
    }

    // ---------- Reliability ----------

    @Test
    void reliability_afterResolvingACase_showsOneCaseAndScore100() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Approved", "Buen trabajo"))).isEqualTo(200);

        MvcResult result = reliability(bobToken, bob);

        assertThat(status(result)).isEqualTo(200);
        assertThat((int) read(result, "$.verifierUserId")).isEqualTo(bob.getId());
        assertThat((int) read(result, "$.resolvedCasesCount")).isEqualTo(1);
        assertThat((int) read(result, "$.overturnedDecisionsCount")).isZero();
        assertThat((int) read(result, "$.sanctionsCount")).isZero();
        assertThat((int) read(result, "$.score")).isEqualTo(100);
    }

    @Test
    void reliability_followsTheRatingOfTheVerifierProfile() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Approved", "Buen trabajo"))).isEqualTo(200);

        assertThat(profileRating(bobToken)).isEqualTo(100.0);
    }

    @Test
    void reliability_whenTheRejectionIsOverturnedOnAppeal_discountsThePreviousVerifier() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Rejected", "Falta evidencia"))).isEqualTo(200);
        enroll(carla, carlaToken);
        assertThat(status(appeal(anaToken, caseId))).isEqualTo(200);
        assertThat((int) read(getCase(anaToken, caseId), "$.verificationCase.verifierUserId"))
                .isEqualTo(carla.getId());

        assertThat(status(decide(carlaToken, caseId, "Approved", "Sí cumple"))).isEqualTo(200);

        MvcResult previous = reliability(bobToken, bob);
        assertThat((int) read(previous, "$.resolvedCasesCount")).isEqualTo(1);
        assertThat((int) read(previous, "$.overturnedDecisionsCount")).isEqualTo(1);
        assertThat((int) read(previous, "$.score")).isEqualTo(85);
        assertThat(profileRating(bobToken)).isEqualTo(85.0);
        assertThat((int) read(reliability(carlaToken, carla), "$.score")).isEqualTo(100);
        assertThat((int) read(employability(anaToken, ana), "$.verifiedSkillsCount")).isEqualTo(1);
    }

    @Test
    void reliability_withoutResolvedCases_returns404() throws Exception {
        MvcResult result = reliability(bobToken, bob);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("ReputationNotFound");
    }

    @Test
    void reliability_ofAnotherVerifier_returns403() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Approved", "Buen trabajo"))).isEqualTo(200);

        MvcResult result = reliability(anaToken, bob);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotReputationOwner");
    }

    @Test
    void reliability_withoutToken_returns401() throws Exception {
        assertThat(status(reliability(null, bob))).isEqualTo(401);
    }
}
