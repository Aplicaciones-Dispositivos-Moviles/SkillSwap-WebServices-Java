package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US37 end to end: a Verificador senior orders the reevaluation of an approved result (a passed attempt or an approved
 * case) and the student has to demonstrate the skill again. Real security, services and PostgreSQL.
 */
class ReevaluationsIntegrationTest extends PostgresIntegrationTest {

    private static final String REEVALUATIONS = "/api/v1/reevaluations";
    private static final String ATTEMPTS = "/api/v1/assessment-attempts";
    private static final String CASES = "/api/v1/verification-cases";
    private static final String PROFILES = "/api/v1/verifier-profiles";
    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SKILL = "networking-basics";
    private static final String JUSTIFICATION = "The answers look copied from the previous assessment.";

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

    @Autowired
    private QuestionGenerationService questionGeneration;

    @Autowired
    private LearningPathContextFacade learningPath;

    private MockMvc mockMvc;
    private User ana;
    private User bob;
    private User carla;
    private User senior;
    private String anaToken;
    private String bobToken;
    private String carlaToken;
    private String seniorToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        FakeQuestionGenerationService generator = (FakeQuestionGenerationService) questionGeneration;
        generator.failWith(null);
        generator.returnQuestionCount(5);
        generator.requests().clear();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        carla = userRepository.save(TestData.newUser("carla", "carla@upc.edu.pe", UserRole.STUDENT));
        senior = userRepository.save(TestData.newUser("sara", "sara@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
        carlaToken = tokenGenerator.generateToken(carla);
        seniorToken = tokenGenerator.generateToken(senior);
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

    private BlueprintView newBlueprint(User student, String token) throws Exception {
        MvcResult path = call(get("/api/v1/learning-paths/" + student.getId()), token, null);
        if (status(path) == 404) {
            assertThat(status(call(post("/api/v1/learning-paths"), token,
                    "{\"goal\":\"" + GOAL + "\"}"))).isEqualTo(201);
            path = call(get("/api/v1/learning-paths/" + student.getId()), token, null);
        }
        List<Integer> nodeIds = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].id");
        MvcResult blueprint = call(post("/api/v1/path-nodes/" + nodeIds.get(0) + "/assessment-blueprint"), token, null);
        assertThat(status(blueprint)).as(body(blueprint)).isEqualTo(201);
        return learningPath.getBlueprint(read(blueprint, "$.id")).orElseThrow();
    }

    private MvcResult submit(String token, BlueprintView blueprint, boolean pass) throws Exception {
        List<Integer> answers = blueprint.questions().stream()
                .map(q -> pass ? q.correctAnswer() : (q.correctAnswer() + 1) % 4).toList();
        MvcResult result = call(post(ATTEMPTS), token,
                "{\"blueprintId\":" + blueprint.blueprintId() + ",\"selectedAnswers\":" + json(answers) + "}");
        assertThat(status(result)).as(body(result)).isEqualTo(201);
        return result;
    }

    /** The student passes the assessment of the skill; returns the id of the attempt. */
    private int passAssessment(User student, String token) throws Exception {
        return read(submit(token, newBlueprint(student, token), true), "$.id");
    }

    /** The student fails the assessment of the skill; returns the id of the case that was opened. */
    private int failAssessment(User student, String token) throws Exception {
        return read(submit(token, newBlueprint(student, token), false), "$.verificationCaseId");
    }

    /** The student completes the skill and becomes a verifier of it. */
    private void enroll(User student, String token) throws Exception {
        passAssessment(student, token);
        MvcResult created = call(post(PROFILES), token, "{\"skillTag\":\"" + SKILL + "\"}");
        assertThat(status(created)).as(body(created)).isEqualTo(201);
    }

    /**
     * A Verificador senior: an enabled verifier (of another skill, so they never take the cases of these tests) with
     * the Gold rank and a reliability of 100.
     */
    private void makeSenior(User user) {
        profileRepository.save(new VerifierProfile(user.getId(), "other-skill"));
        VerifierReliability reliability = new VerifierReliability(user.getId());
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(calculator);
        }
        reliabilityRepository.save(reliability);
    }

    private MvcResult order(String token, String sourceType, Integer sourceId, String justification) throws Exception {
        String id = sourceId == null ? "null" : String.valueOf(sourceId);
        String reason = justification == null ? "null" : "\"" + justification + "\"";
        return call(post(REEVALUATIONS), token,
                "{\"sourceType\":\"" + sourceType + "\",\"sourceId\":" + id + ",\"justification\":" + reason + "}");
    }

    private MvcResult decide(String token, int caseId, String decision) throws Exception {
        return call(patch(CASES + "/" + caseId + "/decision"), token,
                "{\"decision\":\"" + decision + "\",\"rubricNotes\":\"Checked against the rubric.\"}");
    }

    private MvcResult getCase(String token, int caseId) throws Exception {
        return call(get(CASES + "/" + caseId), token, null);
    }

    private String nodeStatus(User student, String token) throws Exception {
        MvcResult path = call(get("/api/v1/learning-paths/" + student.getId()), token, null);
        List<String> statuses = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].status");
        return statuses.get(0);
    }

    private String tokenOf(int userId) {
        return userId == bob.getId() ? bobToken : carlaToken;
    }

    // ---------- Candidates ----------

    private MvcResult candidates(String token, String query) throws Exception {
        return call(get(REEVALUATIONS + "/candidates" + query), token, null);
    }

    private static int count(MvcResult result) throws Exception {
        List<Object> items = read(result, "$");
        return items.size();
    }

    @Test
    @DisplayName("US37: a verifier who is not senior cannot list the candidates")
    void candidates_byWhoIsNotASenior_returns403() throws Exception {
        enroll(bob, bobToken);

        MvcResult result = candidates(bobToken, "");

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotSeniorVerifier");
    }

    @Test
    void candidates_withoutAToken_isRejected() throws Exception {
        assertThat(status(candidates(null, ""))).isIn(401, 403);
    }

    @Test
    @DisplayName("US37: the senior lists the passed attempts of other students, without personal data")
    void candidates_listsThePassedAttemptsOfOtherStudents() throws Exception {
        makeSenior(senior);
        int attemptId = passAssessment(ana, anaToken);

        MvcResult result = candidates(seniorToken, "");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(count(result)).isEqualTo(1);
        assertThat((String) read(result, "$[0].sourceType")).isEqualTo("Attempt");
        assertThat((int) read(result, "$[0].sourceId")).isEqualTo(attemptId);
        assertThat((String) read(result, "$[0].skillTag")).isEqualTo(SKILL);
        assertThat((Object) read(result, "$[0].caseType")).isNull();
        assertThat((String) read(result, "$[0].resolvedAt")).isNotBlank();
        assertThat(body(result)).doesNotContain("studentId");
    }

    @Test
    void candidates_skipTheOwnResultsAndTheAlreadyReevaluatedOnes() throws Exception {
        makeSenior(senior);
        passAssessment(senior, seniorToken);
        int attemptId = passAssessment(ana, anaToken);
        assertThat(status(order(seniorToken, "Attempt", attemptId, JUSTIFICATION))).isEqualTo(201);

        assertThat(count(candidates(seniorToken, ""))).isZero();
    }

    @Test
    void candidates_listsAnApprovedCaseWithItsType() throws Exception {
        makeSenior(senior);
        enroll(bob, bobToken);
        enroll(carla, carlaToken);
        int caseId = failAssessment(ana, anaToken);
        int approver = read(getCase(anaToken, caseId), "$.verificationCase.verifierUserId");
        assertThat(status(decide(tokenOf(approver), caseId, "Approved"))).isEqualTo(200);

        MvcResult result = candidates(seniorToken, "");

        List<Integer> caseIds = read(result, "$[?(@.sourceType=='Case')].sourceId");
        List<String> caseTypes = read(result, "$[?(@.sourceType=='Case')].caseType");
        assertThat(caseIds).containsExactly(caseId);
        assertThat(caseTypes).containsExactly("Quiz");
    }

    @Test
    void candidates_applyTheSkillAndLimitFilters() throws Exception {
        makeSenior(senior);
        passAssessment(ana, anaToken);
        passAssessment(bob, bobToken);

        assertThat(count(candidates(seniorToken, ""))).isEqualTo(2);
        assertThat(count(candidates(seniorToken, "?limit=1"))).isEqualTo(1);
        assertThat(count(candidates(seniorToken, "?skillTag=" + SKILL))).isEqualTo(2);
        assertThat(count(candidates(seniorToken, "?skillTag=unknown-skill"))).isZero();
    }

    // ---------- Attempts ----------

    @Test
    @DisplayName("US37: a senior reevaluates a passed attempt and the student can take the assessment again")
    void order_ofAPassedAttempt_returns201AndTheNodeIsAvailableAgain() throws Exception {
        makeSenior(senior);
        int attemptId = passAssessment(ana, anaToken);
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");

        MvcResult result = order(seniorToken, "Attempt", attemptId, JUSTIFICATION);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).startsWith(REEVALUATIONS + "/");
        assertThat((String) read(result, "$.sourceType")).isEqualTo("Attempt");
        assertThat((int) read(result, "$.sourceId")).isEqualTo(attemptId);
        assertThat((int) read(result, "$.studentId")).isEqualTo(ana.getId());
        assertThat((int) read(result, "$.orderedByUserId")).isEqualTo(senior.getId());
        assertThat((String) read(result, "$.justification")).isEqualTo(JUSTIFICATION);
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Available");
        assertThat(queryString("SELECT count(*) FROM reevaluations")).isEqualTo("1");

        // The student generates a new assessment (the previous questions are excluded) and passes it again.
        BlueprintView again = newBlueprint(ana, anaToken);
        submit(anaToken, again, true);
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
    }

    @Test
    void order_ofTheSameAttemptTwice_returns409TheSecondTime() throws Exception {
        makeSenior(senior);
        int attemptId = passAssessment(ana, anaToken);
        assertThat(status(order(seniorToken, "Attempt", attemptId, JUSTIFICATION))).isEqualTo(201);

        MvcResult second = order(seniorToken, "Attempt", attemptId, JUSTIFICATION);

        assertThat(status(second)).isEqualTo(409);
        assertThat(title(second)).isEqualTo("NodeNotReopenable");
        assertThat(queryString("SELECT count(*) FROM reevaluations")).isEqualTo("1");
    }

    @Test
    void order_ofAFailedAttempt_returns409() throws Exception {
        makeSenior(senior);
        BlueprintView blueprint = newBlueprint(ana, anaToken);
        int attemptId = read(submit(anaToken, blueprint, false), "$.id");

        MvcResult result = order(seniorToken, "Attempt", attemptId, JUSTIFICATION);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("AttemptNotPassed");
    }

    // ---------- Cases ----------

    @Test
    @DisplayName("US37: a senior reevaluates an approved case and another verifier reviews it")
    void order_ofAnApprovedCase_reopensItForAnotherVerifier() throws Exception {
        makeSenior(senior);
        enroll(bob, bobToken);
        enroll(carla, carlaToken);
        int caseId = failAssessment(ana, anaToken);
        int approver = read(getCase(anaToken, caseId), "$.verificationCase.verifierUserId");
        int other = approver == bob.getId() ? carla.getId() : bob.getId();
        assertThat(status(decide(tokenOf(approver), caseId, "Approved"))).isEqualTo(200);
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");

        MvcResult result = order(seniorToken, "Case", caseId, JUSTIFICATION);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat((String) read(result, "$.sourceType")).isEqualTo("Case");
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Available");
        MvcResult reopened = getCase(anaToken, caseId);
        assertThat((String) read(reopened, "$.verificationCase.status")).isEqualTo("Assigned");
        assertThat((int) read(reopened, "$.verificationCase.verifierUserId")).isEqualTo(other);
        assertThat(queryString("SELECT reevaluation_count FROM verification_cases WHERE id = " + caseId))
                .isEqualTo("1");

        // The verifier who approved it first cannot decide it again; the new one approves and the node is completed.
        MvcResult late = decide(tokenOf(approver), caseId, "Approved");
        assertThat(status(late)).isEqualTo(403);
        assertThat(status(decide(tokenOf(other), caseId, "Approved"))).isEqualTo(200);
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
    }

    @Test
    void order_ofARejectedOrOpenCase_returns409() throws Exception {
        makeSenior(senior);
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult open = order(seniorToken, "Case", caseId, JUSTIFICATION);
        assertThat(status(open)).isEqualTo(409);
        assertThat(title(open)).isEqualTo("CaseNotApproved");

        assertThat(status(decide(bobToken, caseId, "Rejected"))).isEqualTo(200);
        MvcResult rejected = order(seniorToken, "Case", caseId, JUSTIFICATION);
        assertThat(status(rejected)).isEqualTo(409);
        assertThat(title(rejected)).isEqualTo("CaseNotApproved");
        assertThat(queryString("SELECT count(*) FROM reevaluations")).isEqualTo("0");
    }

    // ---------- Rules ----------

    @Test
    void order_byWhoIsNotASenior_returns403() throws Exception {
        int attemptId = passAssessment(ana, anaToken);
        enroll(bob, bobToken);

        for (String token : List.of(anaToken, bobToken)) {
            MvcResult result = order(token, "Attempt", attemptId, JUSTIFICATION);
            assertThat(status(result)).isEqualTo(403);
            assertThat(title(result)).isEqualTo("NotSeniorVerifier");
        }
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
        assertThat(queryString("SELECT count(*) FROM reevaluations")).isEqualTo("0");
    }

    @Test
    void order_ofTheOwnResultOfTheSenior_returns403() throws Exception {
        makeSenior(ana);
        int attemptId = passAssessment(ana, anaToken);

        MvcResult result = order(anaToken, "Attempt", attemptId, JUSTIFICATION);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("CannotReevaluateOwnWork");
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
    }

    @Test
    void order_withAnInvalidBody_returns400() throws Exception {
        makeSenior(senior);
        int attemptId = passAssessment(ana, anaToken);

        MvcResult type = order(seniorToken, "Other", attemptId, JUSTIFICATION);
        assertThat(status(type)).isEqualTo(400);
        assertThat(title(type)).isEqualTo("InvalidSourceType");

        for (String blank : new String[]{null, "", "   "}) {
            MvcResult result = order(seniorToken, "Attempt", attemptId, blank);
            assertThat(status(result)).isEqualTo(400);
            assertThat(title(result)).isEqualTo("JustificationRequired");
        }
        MvcResult tooLong = order(seniorToken, "Attempt", attemptId, "j".repeat(2001));
        assertThat(status(tooLong)).isEqualTo(400);
        assertThat(title(tooLong)).isEqualTo("JustificationTooLong");

        assertThat(status(call(post(REEVALUATIONS), seniorToken, null))).isEqualTo(400);
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
    }

    @Test
    void order_ofAnUnknownAttemptOrCase_returns404() throws Exception {
        makeSenior(senior);

        MvcResult attempt = order(seniorToken, "Attempt", 999, JUSTIFICATION);
        assertThat(status(attempt)).isEqualTo(404);
        assertThat(title(attempt)).isEqualTo("AttemptNotFound");

        MvcResult verificationCase = order(seniorToken, "Case", 999, JUSTIFICATION);
        assertThat(status(verificationCase)).isEqualTo(404);
        assertThat(title(verificationCase)).isEqualTo("CaseNotFound");
    }

    @Test
    void order_withoutAToken_isRejected() throws Exception {
        MvcResult result = order(null, "Attempt", 1, JUSTIFICATION);

        assertThat(status(result)).isIn(401, 403);
    }
}
