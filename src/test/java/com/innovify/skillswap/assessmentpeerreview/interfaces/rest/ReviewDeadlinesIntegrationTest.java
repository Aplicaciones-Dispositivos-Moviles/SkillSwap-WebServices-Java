package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerificationCaseCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReassignOverdueCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.infrastructure.scheduling.OverdueCaseReassignmentScheduler;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * US39 end to end: a Verificador senior defines the review deadline of each plan (escenario 1), and an assigned case
 * whose deadline passes is reassigned to another verifier, recording the breach in the reliability of the original
 * one (escenario 2). Real security, services and PostgreSQL.
 */
class ReviewDeadlinesIntegrationTest extends PostgresIntegrationTest {

    private static final String POLICIES = "/api/v1/review-deadline-policies";
    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SKILL = "networking-basics";

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
    private VerificationCaseRepository caseRepository;

    @Autowired
    private VerificationCaseCommandService caseCommands;

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
        request.header("Authorization", "Bearer " + token);
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

    private void enrollVerifier(User user) {
        profileRepository.save(new VerifierProfile(user.getId(), SKILL));
    }

    /** A Verificador senior: an enabled verifier with the Gold rank and a reliability of 100. */
    private void makeSenior(User user) {
        enrollVerifier(user);
        VerifierReliability reliability = new VerifierReliability(user.getId());
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(calculator);
        }
        reliabilityRepository.save(reliability);
    }

    private MvcResult define(String token, Integer premiumHours, Integer freeBusinessDays) throws Exception {
        return call(put(POLICIES), token,
                "{\"premiumPlanHours\":" + premiumHours + ",\"freePlanBusinessDays\":" + freeBusinessDays + "}");
    }

    /** A case of Ana assigned to the verifier, whose review was due an hour ago. */
    private int overdueCaseAssignedTo(User verifier) throws Exception {
        VerificationCase opened = caseRepository.save(new VerificationCase(1, ana.getId(), 1, SKILL, CaseType.QUIZ,
                ReviewDeadline.hours(48)).assignVerifier(verifier.getId()));
        execute("UPDATE verification_cases SET review_due_at = now() - interval '1 hour' WHERE id = " + opened.getId());
        return opened.getId();
    }

    private void runScheduler() {
        new OverdueCaseReassignmentScheduler(caseRepository, caseCommands).reassignOverdueCases();
    }

    private String caseRow(int caseId) throws Exception {
        return queryString("SELECT verifier_user_id || '|' || reassignment_count || '|' || (deadline_missed_at IS NULL)"
                + " || '|' || (review_due_at > now()) FROM verification_cases WHERE id = " + caseId);
    }

    private String missedDeadlines(User verifier) throws Exception {
        return queryString("SELECT missed_deadlines_count || '|' || score FROM verifier_reliabilities "
                + "WHERE verifier_user_id = " + verifier.getId());
    }

    // ---------- Escenario 1: definition of the deadline ----------

    @Test
    void getPolicies_withoutDefinitions_answersTheDeadlinesOfThePlans() throws Exception {
        MvcResult result = call(get(POLICIES), anaToken, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((List<String>) read(result, "$[*].plan")).containsExactly("Premium", "Free");
        assertThat((List<Integer>) read(result, "$[*].amount")).containsExactly(48, 5);
        assertThat((List<String>) read(result, "$[*].unit")).containsExactly("Hours", "BusinessDays");
        assertThat((List<Boolean>) read(result, "$[*].definedBySenior")).containsExactly(false, false);
    }

    @Test
    @DisplayName("US39 E1: a Verificador senior defines the deadline of each plan")
    void define_asASenior_storesBothDeadlines() throws Exception {
        makeSenior(senior);

        MvcResult result = define(seniorToken, 24, 3);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((List<Integer>) read(result, "$[*].amount")).containsExactly(24, 3);
        assertThat((List<Boolean>) read(result, "$[*].definedBySenior")).containsExactly(true, true);
        assertThat((List<Integer>) read(result, "$[*].updatedByUserId"))
                .containsExactly(senior.getId(), senior.getId());

        assertThat(status(define(seniorToken, 48, 5))).isEqualTo(200);
        assertThat((List<Integer>) read(call(get(POLICIES), anaToken, null), "$[*].amount")).containsExactly(48, 5);
    }

    @Test
    void define_byWhoIsNotASenior_returns403() throws Exception {
        enrollVerifier(bob);
        VerifierReliability almost = new VerifierReliability(carla.getId());
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 99; i++) {
            almost.recordResolution(calculator);
        }
        enrollVerifier(carla);
        reliabilityRepository.save(almost);

        for (String token : List.of(anaToken, bobToken, carlaToken)) {
            MvcResult result = define(token, 24, 3);
            assertThat(status(result)).isEqualTo(403);
            assertThat((String) read(result, "$.title")).isEqualTo("NotSeniorVerifier");
        }
        assertThat(queryString("SELECT count(*) FROM review_deadline_policies")).isEqualTo("0");
    }

    @Test
    void define_aSeniorWhoseProfileWasRevoked_returns403() throws Exception {
        makeSenior(senior);
        profileRepository.save(profileRepository.findByUserId(senior.getId()).orElseThrow().revoke());

        assertThat(status(define(seniorToken, 24, 3))).isEqualTo(403);
    }

    @Test
    void define_outOfTheRangeOfThePlan_returns400() throws Exception {
        makeSenior(senior);

        for (MvcResult result : List.of(define(seniorToken, 49, 5), define(seniorToken, 48, 6),
                define(seniorToken, 0, 5), define(seniorToken, null, 5))) {
            assertThat(status(result)).isEqualTo(400);
            assertThat((String) read(result, "$.title")).isEqualTo("InvalidReviewDeadline");
        }
        assertThat(queryString("SELECT count(*) FROM review_deadline_policies")).isEqualTo("0");
    }

    @Test
    @DisplayName("US39 E1: the deadline defined applies to the cases opened from then on")
    void define_appliesToTheCasesOpenedFromThenOn() throws Exception {
        makeSenior(senior);
        assertThat(status(define(seniorToken, 24, 2))).isEqualTo(200);

        assertThat(status(call(post("/api/v1/learning-paths"), anaToken, "{\"goal\":\"" + GOAL + "\"}")))
                .isEqualTo(201);
        MvcResult path = call(get("/api/v1/learning-paths/" + ana.getId()), anaToken, null);
        List<Integer> nodeIds = read(path, "$.nodes[?(@.skillTag=='" + SKILL + "')].id");
        MvcResult blueprint = call(post("/api/v1/path-nodes/" + nodeIds.get(0) + "/assessment-blueprint"), anaToken,
                null);
        BlueprintView view = learningPath.getBlueprint(read(blueprint, "$.id")).orElseThrow();
        String failing = view.questions().stream().map(q -> String.valueOf((q.correctAnswer() + 1) % 4))
                .collect(Collectors.joining(",", "[", "]"));
        MvcResult attempt = call(post("/api/v1/assessment-attempts"), anaToken,
                "{\"blueprintId\":" + view.blueprintId() + ",\"selectedAnswers\":" + failing + "}");
        int caseId = read(attempt, "$.verificationCaseId");

        VerificationCase opened = caseRepository.findById(caseId).orElseThrow();
        assertThat(opened.getReviewDeadline()).isEqualTo(ReviewDeadline.businessDays(2));
        assertThat(Duration.between(opened.getOpenedAt(), opened.getReviewDueAt()))
                .isBetween(Duration.ofDays(2), Duration.ofDays(4));
    }

    // ---------- Escenario 2: overdue case ----------

    @Test
    @DisplayName("US39 E2: an overdue case goes to another verifier and the breach counts in the reliability")
    void overdueCase_isReassignedAndTheBreachIsRecorded() throws Exception {
        enrollVerifier(bob);
        enrollVerifier(carla);
        int caseId = overdueCaseAssignedTo(bob);

        runScheduler();

        assertThat(caseRow(caseId)).isEqualTo(carla.getId() + "|1|true|true");
        VerificationCase reassigned = caseRepository.findById(caseId).orElseThrow();
        assertThat(Duration.between(reassigned.getAssignedAt(), reassigned.getReviewDueAt()))
                .isEqualTo(Duration.ofHours(48));
        assertThat(missedDeadlines(bob)).isEqualTo("1|95");
        assertThat(queryString("SELECT rating FROM verifier_profiles WHERE verifier_user_id = " + bob.getId()))
                .isEqualTo("95");
        assertThat(queryString("SELECT count(*) FROM verifier_reliabilities WHERE verifier_user_id = "
                + carla.getId())).isEqualTo("0");

        // Running again changes nothing: the case is within its new deadline.
        runScheduler();
        assertThat(caseRow(caseId)).isEqualTo(carla.getId() + "|1|true|true");
        assertThat(missedDeadlines(bob)).isEqualTo("1|95");
    }

    @Test
    void overdueCase_neverGoesToItsStudentNorToThePreviousVerifier() throws Exception {
        enrollVerifier(ana);
        enrollVerifier(bob);
        enrollVerifier(carla);
        VerificationCase appealed = caseRepository.save(new VerificationCase(1, ana.getId(), 1, SKILL,
                CaseType.QUIZ, ReviewDeadline.hours(48)).assignVerifier(carla.getId()));
        appealed.resolve(com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision.REJECTED,
                "Missing tests");
        appealed.appeal();
        appealed.assignVerifier(bob.getId());
        caseRepository.save(appealed);
        execute("UPDATE verification_cases SET review_due_at = now() - interval '1 hour' WHERE id = "
                + appealed.getId());

        runScheduler();

        // Ana is the student and Carla rejected it before the appeal: nobody else can take it yet.
        assertThat(caseRow(appealed.getId())).isEqualTo(bob.getId() + "|0|false|false");
        assertThat(missedDeadlines(bob)).isEqualTo("1|95");
    }

    @Test
    void overdueCase_withoutAnotherVerifier_staysAndIsReassignedLaterWithoutASecondBreach() throws Exception {
        enrollVerifier(bob);
        int caseId = overdueCaseAssignedTo(bob);

        runScheduler();
        runScheduler();

        assertThat(caseRow(caseId)).isEqualTo(bob.getId() + "|0|false|false");
        assertThat(missedDeadlines(bob)).isEqualTo("1|95");

        enrollVerifier(carla);
        runScheduler();

        assertThat(caseRow(caseId)).isEqualTo(carla.getId() + "|1|true|true");
        assertThat(missedDeadlines(bob)).isEqualTo("1|95");
    }

    @Test
    void overdueCase_handledTwiceAtTheSameTime_isReassignedOnceWithASingleBreach() throws Exception {
        enrollVerifier(bob);
        enrollVerifier(carla);
        int caseId = overdueCaseAssignedTo(bob);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return caseCommands.handle(new ReassignOverdueCaseCommand(caseId));
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(caseRow(caseId)).isEqualTo(carla.getId() + "|1|true|true");
        assertThat(missedDeadlines(bob)).isEqualTo("1|95");
    }

    @Test
    void theOriginalVerifier_cannotResolveTheCaseOnceReassigned_andTheNewOneCan() throws Exception {
        enrollVerifier(bob);
        enrollVerifier(carla);
        int caseId = overdueCaseAssignedTo(bob);
        runScheduler();
        String decision = "{\"decision\":\"Rejected\",\"rubricNotes\":\"The answers are not justified.\"}";

        MvcResult late = call(patch("/api/v1/verification-cases/" + caseId + "/decision"), bobToken, decision);
        assertThat(status(late)).isEqualTo(403);
        assertThat((String) read(late, "$.title")).isEqualTo("NotAssignedVerifier");

        MvcResult resolved = call(patch("/api/v1/verification-cases/" + caseId + "/decision"), carlaToken, decision);
        assertThat(status(resolved)).as(body(resolved)).isEqualTo(200);
        runScheduler();
        assertThat(queryString("SELECT status FROM verification_cases WHERE id = " + caseId)).isEqualTo("Resolved");
    }

    @Test
    void aCaseWithinItsDeadline_isNotTouched() throws Exception {
        enrollVerifier(bob);
        enrollVerifier(carla);
        VerificationCase onTime = caseRepository.save(new VerificationCase(1, ana.getId(), 1, SKILL, CaseType.QUIZ,
                ReviewDeadline.hours(48)).assignVerifier(bob.getId()));

        runScheduler();

        assertThat(caseRow(onTime.getId())).isEqualTo(bob.getId() + "|0|true|true");
        assertThat(caseRepository.findOverdueAssignedIds(Instant.now())).isEmpty();
        assertThat(queryString("SELECT count(*) FROM verifier_reliabilities")).isEqualTo("0");
    }
}
