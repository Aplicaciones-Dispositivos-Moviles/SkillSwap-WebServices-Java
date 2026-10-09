package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

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
 * The Assessment &amp; Peer Review API end to end: real security filter, JWT, services, Learning Path Engine and
 * PostgreSQL, with the AI replaced by a fake. Nobody has a special role: every user is a student, and being a
 * verifier is a profile acquired by completing the skill.
 */
class AssessmentPeerReviewApiIntegrationTest extends PostgresIntegrationTest {

    private static final String ATTEMPTS = "/api/v1/assessment-attempts";
    private static final String CASES = "/api/v1/verification-cases";
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

    private String nodeStatus(User student, String token) throws Exception {
        MvcResult path = call(get("/api/v1/learning-paths/" + student.getId()), token, null);
        List<String> statuses = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].status");
        return statuses.get(0);
    }

    // ---------- Attempts ----------

    @Test
    void submit_passingAnswers_returns201AndCompletesTheNode() throws Exception {
        MvcResult result = passAssessment(ana, anaToken);

        assertThat(result.getResponse().getHeader("Location")).startsWith(ATTEMPTS + "/");
        assertThat((Boolean) read(result, "$.passed")).isTrue();
        assertThat((Object) read(result, "$.verificationCaseId")).isNull();
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
    }

    @Test
    void submit_failingAnswers_opensAPendingCase() throws Exception {
        BlueprintView blueprint = newBlueprint(ana, anaToken);

        MvcResult result = submit(anaToken, blueprint.blueprintId(), failing(blueprint));

        assertThat(status(result)).isEqualTo(201);
        assertThat((Boolean) read(result, "$.passed")).isFalse();
        assertThat((String) read(result, "$.verificationCaseStatus")).isEqualTo("Pending");
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Available");
    }

    @Test
    void submit_failingAnswersWithAVerifier_assignsTheCaseRightAway() throws Exception {
        enroll(bob, bobToken);

        int caseId = failAssessment(ana, anaToken);

        MvcResult stored = getCase(anaToken, caseId);
        assertThat((String) read(stored, "$.verificationCase.status")).isEqualTo("Assigned");
        assertThat((int) read(stored, "$.verificationCase.verifierUserId")).isEqualTo(bob.getId());
    }

    @Test
    void submit_withTheWrongNumberOfAnswers_returns400() throws Exception {
        BlueprintView blueprint = newBlueprint(ana, anaToken);

        MvcResult result = submit(anaToken, blueprint.blueprintId(), List.of(1, 2));

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidAnswers");
    }

    @Test
    void submit_anotherStudentsAssessment_returns403() throws Exception {
        BlueprintView blueprint = newBlueprint(ana, anaToken);

        MvcResult result = submit(bobToken, blueprint.blueprintId(), passing(blueprint));

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotBlueprintOwner");
    }

    @Test
    void submit_anUnknownAssessment_returns404() throws Exception {
        MvcResult result = submit(anaToken, 9999, List.of(1, 2, 3, 0, 1));

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("BlueprintNotFound");
    }

    @Test
    void submit_theSameAssessmentTwice_returns409() throws Exception {
        BlueprintView blueprint = newBlueprint(ana, anaToken);
        assertThat(status(submit(anaToken, blueprint.blueprintId(), failing(blueprint)))).isEqualTo(201);

        MvcResult again = submit(anaToken, blueprint.blueprintId(), passing(blueprint));

        assertThat(status(again)).isEqualTo(409);
    }

    @Test
    void submit_withoutToken_returns401() throws Exception {
        assertThat(status(submit(null, 1, List.of(1, 2, 3, 0, 1)))).isEqualTo(401);
    }

    @Test
    void getAttempt_asTheOwner_returns200WithoutTheAnswers() throws Exception {
        MvcResult submitted = passAssessment(ana, anaToken);
        int attemptId = read(submitted, "$.id");

        MvcResult result = call(get(ATTEMPTS + "/" + attemptId), anaToken, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((int) read(result, "$.studentId")).isEqualTo(ana.getId());
        assertThat(body(result)).doesNotContain("selectedAnswers").doesNotContain("correctAnswer");
    }

    @Test
    void getAttempt_asAnotherStudent_returns403() throws Exception {
        int attemptId = read(passAssessment(ana, anaToken), "$.id");

        MvcResult result = call(get(ATTEMPTS + "/" + attemptId), bobToken, null);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotAttemptOwner");
    }

    @Test
    void getAttempt_unknown_returns404() throws Exception {
        assertThat(status(call(get(ATTEMPTS + "/9999"), anaToken, null))).isEqualTo(404);
    }

    // ---------- Verifier profiles ----------

    @Test
    void createProfile_withoutCompletingTheSkill_returns409() throws Exception {
        MvcResult result = call(post(PROFILES), bobToken, "{\"skillTag\":\"" + SKILL + "\"}");

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("SkillNotCompleted");
    }

    @Test
    void createProfile_afterCompletingTheSkill_returns201() throws Exception {
        passAssessment(bob, bobToken);

        MvcResult result = call(post(PROFILES), bobToken, "{\"skillTag\":\"" + SKILL + "\"}");

        assertThat(status(result)).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(PROFILES + "/me");
        assertThat((int) read(result, "$.verifierUserId")).isEqualTo(bob.getId());
        assertThat((List<String>) read(result, "$.skillTags")).containsExactly(SKILL);
        assertThat((Boolean) read(result, "$.available")).isTrue();
    }

    @Test
    void createProfile_forTheSameSkillTwice_returns409() throws Exception {
        enroll(bob, bobToken);

        MvcResult result = call(post(PROFILES), bobToken, "{\"skillTag\":\"" + SKILL + "\"}");

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("VerifierSkillAlreadyEnabled");
    }

    @Test
    void createProfile_withoutASkill_returns400() throws Exception {
        MvcResult result = call(post(PROFILES), bobToken, "{}");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidSkillTag");
    }

    @Test
    void getMyProfile_returnsTheCallersOwnProfile() throws Exception {
        enroll(bob, bobToken);

        MvcResult result = call(get(PROFILES + "/me"), bobToken, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((int) read(result, "$.verifierUserId")).isEqualTo(bob.getId());
    }

    @Test
    void getMyProfile_withoutOne_returns404() throws Exception {
        MvcResult result = call(get(PROFILES + "/me"), anaToken, null);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("VerifierProfileNotFound");
    }

    @Test
    void updateAvailability_switchesItOffAndOn() throws Exception {
        enroll(bob, bobToken);

        MvcResult off = call(patch(PROFILES + "/me/availability"), bobToken, "{\"available\":false}");
        assertThat(status(off)).isEqualTo(200);
        assertThat((Boolean) read(off, "$.available")).isFalse();

        MvcResult on = call(patch(PROFILES + "/me/availability"), bobToken, "{\"available\":true}");
        assertThat((Boolean) read(on, "$.available")).isTrue();
    }

    @Test
    void updateAvailability_withoutTheFlag_returns400() throws Exception {
        enroll(bob, bobToken);

        MvcResult result = call(patch(PROFILES + "/me/availability"), bobToken, "{}");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidAvailability");
    }

    @Test
    void updateAvailability_withoutAProfile_returns403() throws Exception {
        MvcResult result = call(patch(PROFILES + "/me/availability"), anaToken, "{\"available\":true}");

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotAVerifier");
    }

    @Test
    void profiles_withoutToken_return401() throws Exception {
        assertThat(status(call(get(PROFILES + "/me"), null, null))).isEqualTo(401);
    }

    // ---------- Cases: reading ----------

    @Test
    void listCases_asAVerifier_returnsOnlyTheirAssignedCases() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = call(get(CASES), bobToken, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((List<Integer>) read(result, "$[*].id")).containsExactly(caseId);
    }

    @Test
    void listCases_asAStudentWhoIsNotAVerifier_returns403() throws Exception {
        MvcResult result = call(get(CASES), anaToken, null);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotAVerifier");
    }

    @Test
    void getCase_asTheStudentAndAsTheAssignedVerifier_returnsTheFailedQuestionsWithoutTheAnswers()
            throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        for (String token : List.of(anaToken, bobToken)) {
            MvcResult result = getCase(token, caseId);

            assertThat(status(result)).isEqualTo(200);
            assertThat((int) read(result, "$.verificationCase.id")).isEqualTo(caseId);
            assertThat((List<Object>) read(result, "$.failedQuestions")).hasSize(5);
            assertThat(body(result)).doesNotContain("correctAnswer");
        }
    }

    @Test
    void getCase_asSomeoneWhoIsNotAPartyOfIt_returns403() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = getCase(danToken, caseId);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotCaseOwner");
    }

    @Test
    void getCase_unknown_returns404() throws Exception {
        assertThat(status(getCase(anaToken, 9999))).isEqualTo(404);
    }

    @Test
    void cases_withoutToken_return401() throws Exception {
        assertThat(status(call(get(CASES), null, null))).isEqualTo(401);
    }

    // ---------- Cases: evidence ----------

    @Test
    void attachEvidence_asTheStudent_returns200() throws Exception {
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = call(put(CASES + "/" + caseId + "/evidence"), anaToken,
                "{\"evidenceUrl\":\"" + EVIDENCE + "\"}");

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.evidenceUrl")).isEqualTo(EVIDENCE);
    }

    @Test
    void attachEvidence_withAnInvalidLink_returns400() throws Exception {
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = call(put(CASES + "/" + caseId + "/evidence"), anaToken,
                "{\"evidenceUrl\":\"ftp://nope\"}");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidEvidenceUrl");
    }

    @Test
    void attachEvidence_asTheVerifier_returns403() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = call(put(CASES + "/" + caseId + "/evidence"), bobToken,
                "{\"evidenceUrl\":\"" + EVIDENCE + "\"}");

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotCaseOwner");
    }

    // ---------- Cases: deciding ----------

    @Test
    void decide_approved_completesTheNodeAndResolvesTheCase() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = decide(bobToken, caseId, "approved", "Meets the rubric.");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.status")).isEqualTo("Resolved");
        assertThat((String) read(result, "$.decision")).isEqualTo("Approved");
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
    }

    @Test
    void decide_rejected_keepsTheNodeAvailable() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = decide(bobToken, caseId, "Rejected", "Needs more practice.");

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.decision")).isEqualTo("Rejected");
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Available");
    }

    @Test
    void decide_withAnInvalidDecision_returns400() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = decide(bobToken, caseId, "Maybe", "Notes.");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidDecision");
    }

    @Test
    void decide_withoutNotes_returns400() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = decide(bobToken, caseId, "Approved", "  ");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("RubricNotesRequired");
    }

    @Test
    void decide_asAVerifierWhoIsNotAssigned_returns403() throws Exception {
        enroll(bob, bobToken);
        enroll(carla, carlaToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = decide(carlaToken, caseId, "Approved", "Notes.");

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotAssignedVerifier");
    }

    @Test
    void decide_asTheStudentOfTheCase_returns403() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult result = decide(anaToken, caseId, "Approved", "I deserve it.");

        assertThat(status(result)).isEqualTo(403);
    }

    @Test
    void decide_twice_returns409() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Rejected", "Needs work."))).isEqualTo(200);

        MvcResult again = decide(bobToken, caseId, "Approved", "Changed my mind.");

        assertThat(status(again)).isEqualTo(409);
        assertThat(title(again)).isEqualTo("CaseAlreadyResolved");
    }

    // ---------- Cases: appeal ----------

    @Test
    void appeal_goesToAnotherVerifierWhoseApprovalCompletesTheNode() throws Exception {
        enroll(bob, bobToken);
        enroll(carla, carlaToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Rejected", "Needs work."))).isEqualTo(200);

        MvcResult appealed = appeal(anaToken, caseId);

        assertThat(status(appealed)).as(body(appealed)).isEqualTo(200);
        assertThat((String) read(appealed, "$.status")).isEqualTo("Assigned");
        assertThat((int) read(appealed, "$.verifierUserId")).isEqualTo(carla.getId());
        assertThat((int) read(appealed, "$.appealCount")).isEqualTo(1);
        assertThat((Object) read(appealed, "$.decision")).isNull();
        assertThat(body(appealed)).doesNotContain("previousVerifier");

        // The first verifier is no longer a party of the case, and cannot decide it again.
        assertThat(status(getCase(bobToken, caseId))).isEqualTo(403);
        assertThat(status(decide(bobToken, caseId, "Approved", "Second thoughts."))).isEqualTo(403);

        MvcResult resolved = decide(carlaToken, caseId, "Approved", "It does meet the rubric.");
        assertThat(status(resolved)).isEqualTo(200);
        assertThat(nodeStatus(ana, anaToken)).isEqualTo("Completed");
    }

    @Test
    void appeal_withoutAnotherVerifier_staysPendingUntilOneIsEnrolled() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Rejected", "Needs work."))).isEqualTo(200);

        MvcResult appealed = appeal(anaToken, caseId);
        assertThat(status(appealed)).isEqualTo(200);
        assertThat((String) read(appealed, "$.status")).isEqualTo("Pending");

        enroll(carla, carlaToken);

        MvcResult stored = getCase(anaToken, caseId);
        assertThat((String) read(stored, "$.verificationCase.status")).isEqualTo("Assigned");
        assertThat((int) read(stored, "$.verificationCase.verifierUserId")).isEqualTo(carla.getId());
    }

    @Test
    void appeal_aCaseThatIsNotRejected_returns409() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult open = appeal(anaToken, caseId);
        assertThat(status(open)).isEqualTo(409);
        assertThat(title(open)).isEqualTo("CaseNotAppealable");

        assertThat(status(decide(bobToken, caseId, "Approved", "Good."))).isEqualTo(200);
        assertThat(status(appeal(anaToken, caseId))).isEqualTo(409);
    }

    @Test
    void appeal_asTheVerifierOrAnotherStudent_returns403() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Rejected", "Needs work."))).isEqualTo(200);

        assertThat(status(appeal(bobToken, caseId))).isEqualTo(403);
        assertThat(status(appeal(danToken, caseId))).isEqualTo(403);
    }

    @Test
    void appeal_twice_returns409() throws Exception {
        enroll(bob, bobToken);
        enroll(carla, carlaToken);
        int caseId = failAssessment(ana, anaToken);
        assertThat(status(decide(bobToken, caseId, "Rejected", "Needs work."))).isEqualTo(200);
        assertThat(status(appeal(anaToken, caseId))).isEqualTo(200);
        assertThat(status(decide(carlaToken, caseId, "Rejected", "Still not enough."))).isEqualTo(200);

        MvcResult again = appeal(anaToken, caseId);

        assertThat(status(again)).isEqualTo(409);
        assertThat(title(again)).isEqualTo("AppealAlreadyUsed");
    }

    @Test
    void appeal_unknownCase_returns404() throws Exception {
        assertThat(status(appeal(anaToken, 9999))).isEqualTo(404);
    }

    @Test
    void appeal_withoutToken_returns401() throws Exception {
        assertThat(status(appeal(null, 1))).isEqualTo(401);
    }
}
