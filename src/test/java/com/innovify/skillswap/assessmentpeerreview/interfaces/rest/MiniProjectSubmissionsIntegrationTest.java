package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.EvaluationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.services.MiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.startup.PendingEvaluationRecovery;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.MiniProjectView;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.repositories.MiniProjectRepository;
import com.innovify.skillswap.learningpathengine.domain.services.MiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
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

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private VerificationCaseRepository cases;

    @Autowired
    private MiniProjectEvaluationService evaluationService;

    @Autowired
    private PendingEvaluationRecovery recovery;

    private FakeMiniProjectEvaluationService ai;
    private MockMvc mockMvc;
    private String anaToken;
    private String bobToken;
    private String carlaToken;
    private User ana;
    private User bob;
    private User carla;
    private int nodeId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ((FakeMiniProjectGenerationService) miniProjectGeneration).reset();
        // By default the AI gives 55 out of 70 with a confidence of 80: the delivery goes to a verifier.
        ai = (FakeMiniProjectEvaluationService) evaluationService;
        ai.reset();
        FakeQuestionGenerationService questions = (FakeQuestionGenerationService) questionGeneration;
        questions.failWith(null);
        questions.returnQuestionCount(5);
        questions.requests().clear();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        carla = userRepository.save(TestData.newUser("carla", "carla@upc.edu.pe", UserRole.STUDENT));
        publisher.publish(new UserRegistered(bob.getId(), UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
        carlaToken = tokenGenerator.generateToken(carla);

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

    /** The student completes the skill on a path of their own and becomes a verifier of it. */
    private void enroll(User student, String token) throws Exception {
        MvcResult path = call(post("/api/v1/learning-paths"), token, "{\"goal\":\"" + GOAL + "\"}");
        assertThat(status(path)).as(body(path)).isEqualTo(201);
        List<Integer> nodeIds = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].id");
        MvcResult blueprint = call(post("/api/v1/path-nodes/" + nodeIds.get(0) + "/assessment-blueprint"), token, null);
        assertThat(status(blueprint)).isEqualTo(201);
        BlueprintView view = learningPath.getBlueprint(read(blueprint, "$.id")).orElseThrow();
        String answers = view.questions().stream().map(q -> String.valueOf(q.correctAnswer()))
                .collect(Collectors.joining(",", "[", "]"));
        MvcResult attempt = call(post("/api/v1/assessment-attempts"), token,
                "{\"blueprintId\":" + view.blueprintId() + ",\"selectedAnswers\":" + answers + "}");
        assertThat(status(attempt)).as(body(attempt)).isEqualTo(201);
        MvcResult profile = call(post("/api/v1/verifier-profiles"), token, "{\"skillTag\":\"" + SKILL + "\"}");
        assertThat(status(profile)).as(body(profile)).isEqualTo(201);
    }

    /** The delivery of the mini-project, as stored after its preliminary evaluation. */
    private MiniProjectSubmission delivery(int miniProjectId) {
        return submissions.findByMiniProjectId(miniProjectId).orElseThrow();
    }

    /** The case the preliminary evaluation opened for the delivery of the mini-project. */
    private int caseOf(int miniProjectId) {
        return cases.findBySubmissionId(delivery(miniProjectId).getId()).orElseThrow().getId();
    }

    /** Ana delivers the mini-project and the AI sends it to a verifier; returns the case. */
    private int deliverToAVerifier(int miniProjectId) throws Exception {
        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);
        assertThat(status(result)).as(body(result)).isEqualTo(201);
        return caseOf(miniProjectId);
    }

    private MvcResult getCase(String token, int caseId) throws Exception {
        return call(get("/api/v1/verification-cases/" + caseId), token, null);
    }

    private MvcResult decide(String token, int caseId, String decision) throws Exception {
        return call(patch("/api/v1/verification-cases/" + caseId + "/decision"), token,
                "{\"decision\":\"" + decision + "\",\"rubricNotes\":\"Reviewed the repository.\"}");
    }

    private MvcResult appeal(String token, int caseId) throws Exception {
        return call(post("/api/v1/verification-cases/" + caseId + "/appeal"), token, null);
    }

    private String nodeStatus() throws Exception {
        MvcResult path = call(get("/api/v1/learning-paths/" + ana.getId()), anaToken, null);
        List<String> statuses = read(path, "$.nodes[?(@.id==" + nodeId + ")].status");
        return statuses.get(0);
    }

    private int balance(String token, User owner) throws Exception {
        MvcResult wallet = call(get("/api/v1/wallets/" + owner.getId()), token, null);
        assertThat(status(wallet)).as(body(wallet)).isEqualTo(200);
        return read(wallet, "$.balance");
    }

    // ---------- Tests ----------

    @Test
    void submit_returns201WithThePendingDeliveryAndSavesIt() throws Exception {
        int miniProjectId = generateMiniProject(false);

        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/mini-projects/" + miniProjectId + "/submissions/" + delivery(miniProjectId).getId());
        assertThat((int) read(result, "$.miniProjectId")).isEqualTo(miniProjectId);
        assertThat((int) read(result, "$.pathNodeId")).isEqualTo(nodeId);
        assertThat((String) read(result, "$.skillTag")).isEqualTo(SKILL);
        assertThat((String) read(result, "$.repositoryUrl")).isEqualTo(REPOSITORY);
        assertThat((String) read(result, "$.submittedAt")).isNotBlank();
        // The answer is given before the evaluation: the app reads its result with the delivery.
        assertThat((String) read(result, "$.evaluationStatus")).isEqualTo("Pending");
        assertThat((Object) read(result, "$.preliminaryScore")).isNull();
        assertThat((Object) read(result, "$.verificationCaseId")).isNull();
        assertThat(submissions.findByMiniProjectId(miniProjectId)).isPresent();
    }

    // ---------- The preliminary evaluation of the AI (US19 escenarios 2 y 3) ----------

    @Test
    void submit_evaluatesTheRepositoryAgainstTheRubricOfTheMiniProject() throws Exception {
        int miniProjectId = generateMiniProject(false);

        submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(ai.requests()).hasSize(1);
        assertThat(ai.requests().get(0).repositoryUrl()).isEqualTo(REPOSITORY);
        assertThat(ai.requests().get(0).brief().skillTag()).isEqualTo(SKILL);
        assertThat(ai.requests().get(0).brief().passingScore()).isEqualTo(MiniProject.PASSING_SCORE);
        assertThat(ai.requests().get(0).brief().rubric()).hasSizeBetween(MiniProject.MIN_CRITERIA,
                MiniProject.MAX_CRITERIA);
    }

    @Test
    void approvedByTheAi_completesTheNodeWithoutACase() throws Exception {
        enroll(bob, bobToken);
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        int miniProjectId = generateMiniProject(false);

        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        MiniProjectSubmission delivery = delivery(miniProjectId);
        assertThat(delivery.getEvaluationStatus()).isEqualTo(EvaluationStatus.APPROVED);
        assertThat(delivery.getPreliminaryScore()).isEqualTo(85);
        assertThat(delivery.getPreliminaryConfidence()).isEqualTo(90);
        assertThat(cases.findBySubmissionId(delivery.getId())).isEmpty();
        assertThat(nodeStatus()).isEqualTo("Completed");
        assertThat(queryString("SELECT count(*) FROM verification_cases")).isEqualTo("0");
    }

    @Test
    void approvedByTheAi_countsTheSkillInTheEmployabilityOfTheStudentOnce() throws Exception {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        int miniProjectId = generateMiniProject(false);

        submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(nodeStatus()).isEqualTo("Completed");
        assertThat(queryString("SELECT verified_skills_count FROM student_employability_scores "
                + "WHERE student_id = " + ana.getId())).isEqualTo("1");
    }

    @Test
    void reviewedByAVerifier_doesNotCountInTheEmployabilityUntilTheyApprove() throws Exception {
        enroll(bob, bobToken);
        int miniProjectId = generateMiniProject(false);

        deliverToAVerifier(miniProjectId);

        assertThat(queryString("SELECT count(*) FROM student_employability_scores WHERE student_id = "
                + ana.getId())).isEqualTo("0");
    }

    @Test
    void aDeliveryLeftPendingByARestart_isEvaluatedWhenTheApplicationRecovers() throws Exception {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        int miniProjectId = generateMiniProject(false);
        // The delivery was saved but the evaluation never ran: the application stopped in between.
        MiniProjectView miniProject = learningPath.getMiniProject(miniProjectId).orElseThrow();
        submissions.save(new MiniProjectSubmission(miniProjectId, miniProject.studentId(),
                miniProject.pathNodeId(), miniProject.skillTag(), REPOSITORY));
        assertThat(delivery(miniProjectId).getEvaluationStatus()).isEqualTo(EvaluationStatus.PENDING);
        assertThat(nodeStatus()).isNotEqualTo("Completed");

        assertThat(recovery.recover()).isEqualTo(1);

        assertThat(delivery(miniProjectId).getEvaluationStatus()).isEqualTo(EvaluationStatus.APPROVED);
        assertThat(nodeStatus()).isEqualTo("Completed");
        assertThat(queryString("SELECT verified_skills_count FROM student_employability_scores "
                + "WHERE student_id = " + ana.getId())).isEqualTo("1");

        // Nothing is pending any more, so a second restart does nothing.
        assertThat(recovery.recover()).isZero();
    }

    @Test
    void withALowConfidence_aVerifierReviewsItEvenWithAHighScore() throws Exception {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.UNSURE);
        int miniProjectId = generateMiniProject(false);

        submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(delivery(miniProjectId).getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(cases.findBySubmissionId(delivery(miniProjectId).getId())).isPresent();
        assertThat(nodeStatus()).isNotEqualTo("Completed");
    }

    @Test
    void whenTheAiFails_aVerifierReviewsIt() throws Exception {
        ai.failWith(new IllegalStateException("Gemini is unavailable"));
        int miniProjectId = generateMiniProject(false);

        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat(delivery(miniProjectId).getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(delivery(miniProjectId).getPreliminaryScore()).isNull();
        assertThat(cases.findBySubmissionId(delivery(miniProjectId).getId())).isPresent();
    }

    // ---------- The verification case of the delivery ----------

    @Test
    void belowThePassingScore_opensAPendingCaseWhenThereIsNoVerifier() throws Exception {
        int miniProjectId = generateMiniProject(false);

        int caseId = deliverToAVerifier(miniProjectId);

        assertThat(delivery(miniProjectId).getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(delivery(miniProjectId).getPreliminaryScore()).isEqualTo(55);
        MvcResult detail = getCase(anaToken, caseId);
        assertThat(status(detail)).as(body(detail)).isEqualTo(200);
        assertThat((String) read(detail, "$.verificationCase.caseType")).isEqualTo("MiniProject");
        assertThat((Object) read(detail, "$.verificationCase.attemptId")).isNull();
        assertThat((int) read(detail, "$.verificationCase.submissionId"))
                .isEqualTo(submissions.findByMiniProjectId(miniProjectId).orElseThrow().getId());
        assertThat((String) read(detail, "$.verificationCase.evidenceUrl")).isEqualTo(REPOSITORY);
        assertThat((Object) read(detail, "$.verificationCase.reviewDueAt")).isNotNull();
        assertThat((Object) read(detail, "$.attempt")).isNull();
        assertThat((List<Object>) read(detail, "$.failedQuestions")).isEmpty();
    }

    @Test
    void belowThePassingScore_assignsTheCaseWhenAVerifierIsEnrolled() throws Exception {
        enroll(bob, bobToken);
        int miniProjectId = generateMiniProject(false);

        int caseId = deliverToAVerifier(miniProjectId);

        MvcResult asVerifier = getCase(bobToken, caseId);
        assertThat((String) read(asVerifier, "$.verificationCase.status")).isEqualTo("Assigned");
        assertThat(status(asVerifier)).isEqualTo(200);
        assertThat((int) read(asVerifier, "$.verificationCase.verifierUserId")).isEqualTo(bob.getId());
        assertThat(status(getCase(carlaToken, caseId))).isEqualTo(403);
    }

    /** Ana used the three escalations of the month of the free plan, on other nodes. */
    private void useTheEscalationsOfTheMonth() throws Exception {
        int anaId = ana.getId();
        for (int i = 0; i < 3; i++) {
            execute("INSERT INTO verification_cases (attempt_id, student_id, path_node_id, skill_tag, case_type, "
                    + "status, opened_at, appeal_count) VALUES (" + (9000 + i) + ", " + anaId + ", " + (9000 + i)
                    + ", 'sql-fundamentals', 'Quiz', 'Pending', now(), 0)");
        }
    }

    @Test
    void belowThePassingScore_afterTheEscalationsOfTheMonth_keepsTheDeliveryWithoutACase() throws Exception {
        useTheEscalationsOfTheMonth();
        int miniProjectId = generateMiniProject(false);

        MvcResult result = submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat(delivery(miniProjectId).getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATION_LIMIT_REACHED);
        assertThat(queryString("SELECT count(*) FROM verification_cases")).isEqualTo("3");
        assertThat(nodeStatus()).isNotEqualTo("Completed");
    }

    @Test
    void approvedByTheAi_afterTheEscalationsOfTheMonth_stillCompletesTheNode() throws Exception {
        useTheEscalationsOfTheMonth();
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        int miniProjectId = generateMiniProject(false);

        submit(anaToken, miniProjectId, REPOSITORY);

        assertThat(delivery(miniProjectId).getEvaluationStatus()).isEqualTo(EvaluationStatus.APPROVED);
        assertThat(nodeStatus()).isEqualTo("Completed");
    }

    @Test
    void approvedByTheVerifier_completesTheNodeAndPaysTheVerifier40SkillCredits() throws Exception {
        enroll(bob, bobToken);
        int miniProjectId = generateMiniProject(false);
        int caseId = deliverToAVerifier(miniProjectId);
        assertThat(nodeStatus()).isNotEqualTo("Completed");

        MvcResult resolved = decide(bobToken, caseId, "Approved");

        assertThat(status(resolved)).as(body(resolved)).isEqualTo(200);
        assertThat((String) read(resolved, "$.status")).isEqualTo("Resolved");
        assertThat((String) read(resolved, "$.decision")).isEqualTo("Approved");
        assertThat(nodeStatus()).isEqualTo("Completed");
        assertThat(balance(bobToken, bob)).isEqualTo(40);
    }

    @Test
    void rejectedByTheVerifier_theStudentAppealsOnce() throws Exception {
        enroll(bob, bobToken);
        enroll(carla, carlaToken);
        int miniProjectId = generateMiniProject(false);
        int caseId = deliverToAVerifier(miniProjectId);
        assertThat(status(decide(bobToken, caseId, "Rejected"))).isEqualTo(200);
        assertThat(nodeStatus()).isNotEqualTo("Completed");

        MvcResult appealed = appeal(anaToken, caseId);

        assertThat(status(appealed)).as(body(appealed)).isEqualTo(200);
        assertThat((String) read(appealed, "$.status")).isEqualTo("Assigned");
        assertThat((int) read(appealed, "$.verifierUserId")).isEqualTo(carla.getId());
        assertThat((int) read(appealed, "$.appealCount")).isEqualTo(1);
        assertThat(status(decide(carlaToken, caseId, "Rejected"))).isEqualTo(200);

        MvcResult again = appeal(anaToken, caseId);
        assertThat(status(again)).isEqualTo(409);
        assertThat(title(again)).isEqualTo("AppealAlreadyUsed");
        assertThat(nodeStatus()).isNotEqualTo("Completed");
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

    // ---------- Reading the delivery and its evaluation ----------

    private MvcResult getDelivery(String token, int miniProjectId, int submissionId) throws Exception {
        return call(get("/api/v1/mini-projects/" + miniProjectId + "/submissions/" + submissionId), token, null);
    }

    @Test
    void getDelivery_sentToAVerifier_showsTheEvaluationAndTheCase() throws Exception {
        int miniProjectId = generateMiniProject(false);
        String location = submit(anaToken, miniProjectId, REPOSITORY).getResponse().getHeader("Location");

        MvcResult result = call(get(location), anaToken, null);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((int) read(result, "$.id")).isEqualTo(delivery(miniProjectId).getId());
        assertThat((String) read(result, "$.evaluationStatus")).isEqualTo("Escalated");
        assertThat((int) read(result, "$.preliminaryScore")).isEqualTo(55);
        assertThat((int) read(result, "$.preliminaryConfidence")).isEqualTo(80);
        assertThat((String) read(result, "$.preliminaryFeedback"))
                .isEqualTo(FakeMiniProjectEvaluationService.NOT_ENOUGH.feedback());
        assertThat((String) read(result, "$.evaluatedAt")).isNotBlank();
        assertThat((int) read(result, "$.verificationCaseId")).isEqualTo(caseOf(miniProjectId));
        assertThat((String) read(result, "$.verificationCaseStatus")).isEqualTo("Pending");
    }

    @Test
    void getDelivery_approvedByTheAi_hasNoCase() throws Exception {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        int miniProjectId = generateMiniProject(false);
        int submissionId = read(submit(anaToken, miniProjectId, REPOSITORY), "$.id");

        MvcResult result = getDelivery(anaToken, miniProjectId, submissionId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.evaluationStatus")).isEqualTo("Approved");
        assertThat((int) read(result, "$.preliminaryScore")).isEqualTo(85);
        assertThat((Object) read(result, "$.verificationCaseId")).isNull();
    }

    @Test
    void getDelivery_withoutEscalationsLeft_reportsIt() throws Exception {
        useTheEscalationsOfTheMonth();
        int miniProjectId = generateMiniProject(false);
        int submissionId = read(submit(anaToken, miniProjectId, REPOSITORY), "$.id");

        MvcResult result = getDelivery(anaToken, miniProjectId, submissionId);

        assertThat((String) read(result, "$.evaluationStatus")).isEqualTo("EscalationLimitReached");
        assertThat((Object) read(result, "$.verificationCaseId")).isNull();
    }

    @Test
    void getDelivery_ofAnotherStudent_returns403() throws Exception {
        int miniProjectId = generateMiniProject(false);
        int submissionId = read(submit(anaToken, miniProjectId, REPOSITORY), "$.id");

        MvcResult result = getDelivery(bobToken, miniProjectId, submissionId);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotSubmissionOwner");
    }

    @Test
    void getDelivery_unknownOrOfAnotherMiniProject_returns404() throws Exception {
        int miniProjectId = generateMiniProject(false);
        int submissionId = read(submit(anaToken, miniProjectId, REPOSITORY), "$.id");

        MvcResult unknown = getDelivery(anaToken, miniProjectId, 9999);
        MvcResult otherMiniProject = getDelivery(anaToken, miniProjectId + 1, submissionId);

        assertThat(status(unknown)).isEqualTo(404);
        assertThat(title(unknown)).isEqualTo("MiniProjectSubmissionNotFound");
        assertThat(status(otherMiniProject)).isEqualTo(404);
    }

    @Test
    void getDelivery_withoutToken_returns401() throws Exception {
        assertThat(status(getDelivery(null, 1, 1))).isEqualTo(401);
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
