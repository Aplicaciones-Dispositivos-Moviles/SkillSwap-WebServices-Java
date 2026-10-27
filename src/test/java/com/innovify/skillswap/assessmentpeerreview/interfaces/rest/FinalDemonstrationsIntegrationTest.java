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
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
 * US28 end to end: the student schedules the final demonstration of a completed path, changes or cancels it, and the
 * verifier sees it. Real security, services and PostgreSQL.
 */
class FinalDemonstrationsIntegrationTest extends PostgresIntegrationTest {

    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SKILL = "networking-basics";
    private static final String DEMONSTRATIONS = "/api/v1/final-demonstrations";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private LearningPathContextFacade learningPath;

    @Autowired
    private LearningPathRepository paths;

    @Autowired
    private VerifierProfileRepository profiles;

    private MockMvc mockMvc;
    private String anaToken;
    private String bobToken;
    private String carlaToken;
    private User ana;
    private User bob;
    private int pathId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        User carla = userRepository.save(TestData.newUser("carla", "carla@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
        carlaToken = tokenGenerator.generateToken(carla);

        MvcResult path = call(post("/api/v1/learning-paths"), anaToken, "{\"goal\":\"" + GOAL + "\"}");
        assertThat(status(path)).as(body(path)).isEqualTo(201);
        pathId = read(path, "$.id");
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

    private static String inDays(int days) {
        return Instant.now().plus(Duration.ofDays(days)).toString();
    }

    /** Completes every node of the path, in as many passes as the prerequisites need. */
    private void completePath() {
        for (int pass = 0; pass < 20; pass++) {
            var path = paths.findById(pathId).orElseThrow();
            if (path.getStatus() == PathStatus.COMPLETED) {
                return;
            }
            path.getNodes().forEach(node -> learningPath.completeNode(node.getId()));
        }
        assertThat(paths.findById(pathId).orElseThrow().getStatus()).isEqualTo(PathStatus.COMPLETED);
    }

    private void enableBobAsVerifier() {
        profiles.save(new VerifierProfile(bob.getId(), SKILL));
    }

    private MvcResult schedule(String token, int learningPathId, String modality, String scheduledAt) throws Exception {
        String json = "{\"learningPathId\":" + learningPathId + ",\"modality\":"
                + (modality == null ? "null" : "\"" + modality + "\"") + ",\"scheduledAt\":"
                + (scheduledAt == null ? "null" : "\"" + scheduledAt + "\"") + "}";
        return call(post(DEMONSTRATIONS), token, json);
    }

    private int scheduleOk() throws Exception {
        MvcResult result = schedule(anaToken, pathId, "AdvancedProject", inDays(3));
        assertThat(status(result)).as(body(result)).isEqualTo(201);
        return read(result, "$.id");
    }

    // ---------- Schedule ----------

    @Test
    void schedule_withoutToken_isUnauthorized() throws Exception {
        assertThat(status(schedule(null, pathId, "AdvancedProject", inDays(3)))).isEqualTo(401);
    }

    @Test
    void schedule_ofACompletedPath_assignsTheVerifier() throws Exception {
        completePath();
        enableBobAsVerifier();

        MvcResult result = schedule(anaToken, pathId, "SupervisedExam", inDays(3));

        assertThat(status(result)).as(body(result)).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).startsWith(DEMONSTRATIONS + "/");
        assertThat((Integer) read(result, "$.studentId")).isEqualTo(ana.getId());
        assertThat((Integer) read(result, "$.verifierUserId")).isEqualTo(bob.getId());
        assertThat((Integer) read(result, "$.learningPathId")).isEqualTo(pathId);
        assertThat((String) read(result, "$.skillTag")).isEqualTo(SKILL);
        assertThat((String) read(result, "$.modality")).isEqualTo("SupervisedExam");
        assertThat((String) read(result, "$.status")).isEqualTo("Scheduled");
    }

    @Test
    void schedule_ofAPathWithPendingNodes_isConflict() throws Exception {
        enableBobAsVerifier();

        MvcResult result = schedule(anaToken, pathId, "AdvancedProject", inDays(3));

        assertThat(status(result)).as(body(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("PathNotCompleted");
    }

    @Test
    void schedule_withoutVerifiers_isConflict() throws Exception {
        completePath();

        MvcResult result = schedule(anaToken, pathId, "AdvancedProject", inDays(3));

        assertThat(status(result)).as(body(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("NoVerifierAvailable");
    }

    @Test
    void schedule_withInvalidData_isBadRequest() throws Exception {
        completePath();
        enableBobAsVerifier();

        MvcResult modality = schedule(anaToken, pathId, "Presential", inDays(3));
        MvcResult missingModality = schedule(anaToken, pathId, null, inDays(3));
        MvcResult missingMoment = schedule(anaToken, pathId, "AdvancedProject", null);

        assertThat(status(modality)).isEqualTo(400);
        assertThat(title(modality)).isEqualTo("InvalidModality");
        assertThat(status(missingModality)).isEqualTo(400);
        assertThat(status(missingMoment)).isEqualTo(400);
        assertThat(title(missingMoment)).isEqualTo("InvalidSchedule");
    }

    @Test
    void schedule_lessThan24HoursAhead_isConflict() throws Exception {
        completePath();
        enableBobAsVerifier();

        MvcResult result = schedule(anaToken, pathId, "AdvancedProject",
                Instant.now().plus(Duration.ofHours(3)).toString());

        assertThat(status(result)).as(body(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("ScheduleTooSoon");
    }

    @Test
    void schedule_ofAnotherStudentsOrUnknownPath_isForbiddenOrNotFound() throws Exception {
        completePath();
        enableBobAsVerifier();

        MvcResult notOwner = schedule(carlaToken, pathId, "AdvancedProject", inDays(3));
        MvcResult unknown = schedule(anaToken, 9999, "AdvancedProject", inDays(3));

        assertThat(status(notOwner)).isEqualTo(403);
        assertThat(title(notOwner)).isEqualTo("NotPathOwner");
        assertThat(status(unknown)).isEqualTo(404);
        assertThat(title(unknown)).isEqualTo("LearningPathNotFound");
    }

    @Test
    void schedule_twice_isConflictUntilTheFirstIsCancelled() throws Exception {
        completePath();
        enableBobAsVerifier();
        int first = scheduleOk();

        MvcResult second = schedule(anaToken, pathId, "AdvancedProject", inDays(4));
        assertThat(status(second)).isEqualTo(409);
        assertThat(title(second)).isEqualTo("DemonstrationAlreadyActive");

        assertThat(status(call(patch(DEMONSTRATIONS + "/" + first + "/cancel"), anaToken, null))).isEqualTo(200);
        assertThat(status(schedule(anaToken, pathId, "AdvancedProject", inDays(4)))).isEqualTo(201);
    }

    // ---------- Read ----------

    @Test
    void getById_isForTheStudentAndTheVerifierOnly() throws Exception {
        completePath();
        enableBobAsVerifier();
        int id = scheduleOk();

        assertThat(status(call(get(DEMONSTRATIONS + "/" + id), anaToken, null))).isEqualTo(200);
        assertThat(status(call(get(DEMONSTRATIONS + "/" + id), bobToken, null))).isEqualTo(200);
        MvcResult stranger = call(get(DEMONSTRATIONS + "/" + id), carlaToken, null);
        assertThat(status(stranger)).isEqualTo(403);
        assertThat(title(stranger)).isEqualTo("NotDemonstrationParticipant");
        MvcResult unknown = call(get(DEMONSTRATIONS + "/9999"), anaToken, null);
        assertThat(status(unknown)).isEqualTo(404);
        assertThat(title(unknown)).isEqualTo("DemonstrationNotFound");
    }

    @Test
    void getMineAndAssigned_listEachPartysDemonstrations() throws Exception {
        completePath();
        enableBobAsVerifier();
        int id = scheduleOk();

        MvcResult mine = call(get(DEMONSTRATIONS + "/me"), anaToken, null);
        MvcResult assigned = call(get(DEMONSTRATIONS + "/assigned"), bobToken, null);
        MvcResult notAVerifier = call(get(DEMONSTRATIONS + "/assigned"), carlaToken, null);

        assertThat(status(mine)).isEqualTo(200);
        assertThat(JsonPath.<List<Integer>>read(body(mine), "$[*].id")).containsExactly(id);
        assertThat(JsonPath.<List<Integer>>read(body(assigned), "$[*].id")).containsExactly(id);
        assertThat(JsonPath.<List<Integer>>read(body(notAVerifier), "$[*].id")).isEmpty();
        assertThat(JsonPath.<List<Integer>>read(body(call(get(DEMONSTRATIONS + "/me"), bobToken, null)), "$[*].id"))
                .isEmpty();
    }

    // ---------- Reschedule and cancel ----------

    @Test
    void reschedule_movesTheDemonstration() throws Exception {
        completePath();
        enableBobAsVerifier();
        int id = scheduleOk();
        String later = Instant.now().plus(Duration.ofDays(6)).toString();

        MvcResult result = call(patch(DEMONSTRATIONS + "/" + id + "/reschedule"), anaToken,
                "{\"scheduledAt\":\"" + later + "\"}");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(Instant.parse(read(result, "$.scheduledAt"))).isEqualTo(Instant.parse(later));
        assertThat((String) read(result, "$.status")).isEqualTo("Scheduled");
    }

    @Test
    void reschedule_withBadDataOrByAnotherStudent_isRejected() throws Exception {
        completePath();
        enableBobAsVerifier();
        int id = scheduleOk();

        MvcResult tooSoon = call(patch(DEMONSTRATIONS + "/" + id + "/reschedule"), anaToken,
                "{\"scheduledAt\":\"" + Instant.now().plus(Duration.ofHours(2)) + "\"}");
        MvcResult missing = call(patch(DEMONSTRATIONS + "/" + id + "/reschedule"), anaToken, "{}");
        MvcResult other = call(patch(DEMONSTRATIONS + "/" + id + "/reschedule"), carlaToken,
                "{\"scheduledAt\":\"" + inDays(6) + "\"}");

        assertThat(status(tooSoon)).isEqualTo(409);
        assertThat(title(tooSoon)).isEqualTo("ScheduleTooSoon");
        assertThat(status(missing)).isEqualTo(400);
        assertThat(title(missing)).isEqualTo("InvalidSchedule");
        assertThat(status(other)).isEqualTo(403);
        assertThat(title(other)).isEqualTo("NotDemonstrationOwner");
    }

    @Test
    void cancel_thenChangingIt_isConflict() throws Exception {
        completePath();
        enableBobAsVerifier();
        int id = scheduleOk();

        MvcResult cancelled = call(patch(DEMONSTRATIONS + "/" + id + "/cancel"), anaToken, null);
        MvcResult again = call(patch(DEMONSTRATIONS + "/" + id + "/cancel"), anaToken, null);
        MvcResult reschedule = call(patch(DEMONSTRATIONS + "/" + id + "/reschedule"), anaToken,
                "{\"scheduledAt\":\"" + inDays(6) + "\"}");

        assertThat(status(cancelled)).isEqualTo(200);
        assertThat((String) read(cancelled, "$.status")).isEqualTo("Cancelled");
        assertThat(status(again)).isEqualTo(409);
        assertThat(title(again)).isEqualTo("DemonstrationNotScheduled");
        assertThat(status(reschedule)).isEqualTo(409);
        assertThat(JsonPath.<List<Integer>>read(body(call(get(DEMONSTRATIONS + "/assigned"), bobToken, null)),
                "$[*].id")).isEmpty();
    }
}
