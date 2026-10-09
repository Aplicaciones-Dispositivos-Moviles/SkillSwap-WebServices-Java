package com.innovify.skillswap.recognitionincentives.interfaces.rest;

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
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
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
 * The wallet API end to end, fed by the real events of Assessment &amp; Peer Review and IAM. A wallet is private to
 * its owner.
 */
class RecognitionIncentivesApiIntegrationTest extends PostgresIntegrationTest {

    private static final String ATTEMPTS = "/api/v1/assessment-attempts";
    private static final String CASES = "/api/v1/verification-cases";
    private static final String WALLETS = "/api/v1/wallets/";
    private static final String REDEEM = "/api/v1/credit-transactions/redeem";
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
    private int nextCaseId;
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
        nextCaseId = 1000;
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        FakeQuestionGenerationService generator = (FakeQuestionGenerationService) questionGeneration;
        generator.failWith(null);
        generator.returnQuestionCount(5);
        generator.requests().clear();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        carla = userRepository.save(TestData.newUser("carla", "carla@upc.edu.pe", UserRole.STUDENT));
        dan = userRepository.save(TestData.newUser("dan", "dan@upc.edu.pe", UserRole.STUDENT));
        publisher.publish(new UserRegistered(ana.getId(), UserRole.STUDENT));
        publisher.publish(new UserRegistered(bob.getId(), UserRole.STUDENT));
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

    /** The verifier earns 10 SkillCredits for each case, as if they had resolved that many. */
    private void earn(User verifier, int credits) {
        for (int i = 0; i < credits / 10; i++) {
            publisher.publish(new VerificationCaseResolved(nextCaseId++, ana.getId(), verifier.getId(), 1,
                    SKILL, ReviewDecision.APPROVED, null));
        }
    }

    private MvcResult wallet(String token, User owner) throws Exception {
        return call(get(WALLETS + owner.getId()), token, null);
    }

    private MvcResult history(String token, User owner) throws Exception {
        return call(get(WALLETS + owner.getId() + "/transactions"), token, null);
    }

    private MvcResult redeem(String token, String item) throws Exception {
        return call(post(REDEEM), token, item == null ? "{}" : "{\"item\":\"" + item + "\"}");
    }

    private int balance(String token, User owner) throws Exception {
        return read(wallet(token, owner), "$.balance");
    }

    // ---------- Earning ----------

    @Test
    void resolvingACase_asApproved_creditsTheVerifier() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult resolved = decide(bobToken, caseId, "Approved", "Solid understanding of the network layers.");

        assertThat(status(resolved)).isEqualTo(200);
        assertThat(balance(bobToken, bob)).isEqualTo(10);
    }

    @Test
    void resolvingACase_asRejected_alsoCreditsTheVerifier() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        MvcResult resolved = decide(bobToken, caseId, "Rejected", "The explanation of the layers is incomplete.");

        assertThat(status(resolved)).isEqualTo(200);
        assertThat(balance(bobToken, bob)).isEqualTo(10);
    }

    @Test
    void resolvingACase_doesNotCreditTheStudentOfTheCase() throws Exception {
        enroll(bob, bobToken);
        int caseId = failAssessment(ana, anaToken);

        decide(bobToken, caseId, "Approved", "Solid understanding of the network layers.");

        assertThat(balance(anaToken, ana)).isZero();
    }

    // ---------- Consulting ----------

    @Test
    void wallet_ofANewAccount_isEmpty() throws Exception {
        MvcResult result = wallet(anaToken, ana);

        assertThat(status(result)).isEqualTo(200);
        assertThat((int) read(result, "$.walletOwnerId")).isEqualTo(ana.getId());
        assertThat((int) read(result, "$.balance")).isZero();
    }

    @Test
    void wallet_ofAnAccountCreatedBeforeTheWallets_returns404() throws Exception {
        MvcResult result = wallet(carlaToken, carla);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("WalletNotFound");
    }

    @Test
    void wallet_ofAnotherUser_returns403() throws Exception {
        MvcResult result = wallet(anaToken, bob);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotWalletOwner");
    }

    @Test
    void wallet_withoutToken_returns401() throws Exception {
        assertThat(status(wallet(null, ana))).isEqualTo(401);
    }

    @Test
    void history_listsTheMovementsFromTheMostRecent() throws Exception {
        earn(bob, 30);
        assertThat(status(redeem(bobToken, "ContributionCertificate"))).isEqualTo(201);

        MvcResult result = history(bobToken, bob);

        assertThat(status(result)).isEqualTo(200);
        assertThat((List<Object>) read(result, "$")).hasSize(4);
        assertThat((String) read(result, "$[0].type")).isEqualTo("Redeemed");
        assertThat((int) read(result, "$[0].amount")).isEqualTo(30);
        assertThat((String) read(result, "$[3].type")).isEqualTo("Earned");
        assertThat((int) read(result, "$[3].relatedCaseId")).isEqualTo(1000);
    }

    @Test
    void history_ofAnotherUser_returns403() throws Exception {
        MvcResult result = history(anaToken, bob);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotWalletOwner");
    }

    @Test
    void history_ofAnAccountWithoutWallet_returns404() throws Exception {
        MvcResult result = history(carlaToken, carla);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("WalletNotFound");
    }

    // ---------- Redeeming ----------

    @Test
    void redeem_withEnoughBalance_returns201AndTakesTheCost() throws Exception {
        earn(bob, 30);

        MvcResult result = redeem(bobToken, "ContributionCertificate");

        assertThat(status(result)).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(WALLETS + bob.getId() + "/transactions");
        assertThat((String) read(result, "$.type")).isEqualTo("Redeemed");
        assertThat((int) read(result, "$.amount")).isEqualTo(30);
        assertThat((Object) read(result, "$.relatedCaseId")).isNull();
        assertThat(balance(bobToken, bob)).isZero();
    }

    @Test
    void redeem_withInsufficientBalance_returns409AndKeepsTheBalance() throws Exception {
        earn(bob, 20);

        MvcResult result = redeem(bobToken, "ContributionCertificate");

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("InsufficientBalance");
        assertThat(balance(bobToken, bob)).isEqualTo(20);
    }

    @Test
    void redeem_theAdvancedPathUnlock_costsFiftyCredits() throws Exception {
        earn(bob, 40);
        MvcResult notEnough = redeem(bobToken, "AdvancedPathUnlock");
        assertThat(status(notEnough)).isEqualTo(409);
        assertThat(title(notEnough)).isEqualTo("InsufficientBalance");

        earn(bob, 10);

        MvcResult result = redeem(bobToken, "AdvancedPathUnlock");

        assertThat(status(result)).isEqualTo(201);
        assertThat((int) read(result, "$.amount")).isEqualTo(50);
        assertThat(balance(bobToken, bob)).isZero();
    }

    @Test
    void redeem_aStudentWithoutCredits_returns409() throws Exception {
        MvcResult result = redeem(anaToken, "ContributionCertificate");

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("InsufficientBalance");
    }

    @Test
    void redeem_withoutWallet_returns404() throws Exception {
        MvcResult result = redeem(carlaToken, "ContributionCertificate");

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("WalletNotFound");
    }

    @Test
    void redeem_anUnknownBenefit_returns400() throws Exception {
        MvcResult result = redeem(anaToken, "coffee");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidRedemptionItem");
    }

    @Test
    void redeem_withoutItem_returns400() throws Exception {
        MvcResult result = redeem(anaToken, null);

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("InvalidRedemptionItem");
    }

    @Test
    void redeem_acceptsTheNameInAnyCase() throws Exception {
        earn(bob, 30);

        assertThat(status(redeem(bobToken, "contributioncertificate"))).isEqualTo(201);
    }

    @Test
    void redeem_withoutToken_returns401() throws Exception {
        assertThat(status(redeem(null, "ContributionCertificate"))).isEqualTo(401);
    }
}
