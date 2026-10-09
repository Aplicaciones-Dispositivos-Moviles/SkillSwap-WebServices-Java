package com.innovify.skillswap.learningpathengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.repositories.WalletRepository;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.subscriptionbilling.application.commandservices.SubscriptionCommandService;
import com.innovify.skillswap.subscriptionbilling.application.fakes.FakePaymentGateway;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CreateSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.commands.ProcessRevenueCatEventCommand;
import com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects.PurchaseVerification;
import com.innovify.skillswap.subscriptionbilling.domain.services.PaymentGateway;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * US05 (escenarios 4 y 6) end to end: the advanced path redeemed with SkillCredits is granted as an unlock of the
 * student, the path started with it does not count toward the limits of the plan, and a downgrade keeps it active.
 */
class AdvancedPathUnlockIntegrationTest extends PostgresIntegrationTest {

    private static final String REST_AND_JWT = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SQL = "quiero aprender SQL";
    private static final String HTTP = "quiero aprender HTTP";
    private static final String DOCKER = "quiero aprender Docker y contenedores";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LearningPathCommandService commands;

    @Autowired
    private LearningPathRepository paths;

    @Autowired
    private SubscriptionCommandService subscriptions;

    @Autowired
    private PaymentGateway paymentGateway;

    private MockMvc mockMvc;
    private User ana;
    private String anaToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ((FakePaymentGateway) paymentGateway).reset();
        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
    }

    private MvcResult call(MockHttpServletRequestBuilder request, String json) throws Exception {
        request.header("Authorization", "Bearer " + anaToken);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    private void giveCredits(int amount) {
        Wallet wallet = walletRepository.findByOwnerId(ana.getId()).orElseGet(() -> new Wallet(ana.getId()));
        walletRepository.save(wallet.credit(new Credits(amount)));
    }

    private MvcResult redeemAdvancedPath() throws Exception {
        MvcResult result = call(post("/api/v1/credit-transactions/redeem"), "{\"item\":\"AdvancedPathUnlock\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return result;
    }

    private MvcResult declare(String goal, boolean advanced) throws Exception {
        return call(post("/api/v1/learning-paths"),
                "{\"goal\":\"" + goal + "\",\"advanced\":" + advanced + "}");
    }

    private Result<LearningPath> declareDirectly(String goal) {
        return commands.handle(new DeclareGoalCommand(ana.getId(), goal));
    }

    @Test
    @DisplayName("US32/US05: redeeming the advanced path grants an available unlock to the student")
    void redeem_advancedPath_grantsAnAvailableUnlock() throws Exception {
        giveCredits(200);

        MvcResult redemption = redeemAdvancedPath();
        int redemptionId = read(redemption, "$.id");
        assertThat((String) read(redemption, "$.redemptionItem")).isEqualTo("AdvancedPathUnlock");

        MvcResult unlocks = call(get("/api/v1/advanced-path-unlocks"), null);
        assertThat(unlocks.getResponse().getStatus()).isEqualTo(200);
        assertThat((List<Integer>) read(unlocks, "$[*].redemptionId")).containsExactly(redemptionId);
        assertThat((List<String>) read(unlocks, "$[*].status")).containsExactly("Available");
        assertThat(queryString("SELECT count(*) FROM advanced_path_unlocks")).isEqualTo("1");
    }

    @Test
    @DisplayName("US05 E6: on the free plan the advanced path is not counted in the limit of active paths")
    void freePlan_withOneActivePath_canStillStartTheAdvancedPath() throws Exception {
        giveCredits(200);
        redeemAdvancedPath();
        assertThat(declare(REST_AND_JWT, false).getResponse().getStatus()).isEqualTo(201);

        MvcResult regular = declare(SQL, false);
        assertThat(regular.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) read(regular, "$.title")).isEqualTo("PlanLimitReached");

        MvcResult advanced = declare(SQL, true);
        assertThat(advanced.getResponse().getStatus()).isEqualTo(201);
        assertThat((Boolean) read(advanced, "$.advanced")).isTrue();
        int advancedId = read(advanced, "$.id");

        MvcResult unlocks = call(get("/api/v1/advanced-path-unlocks"), null);
        assertThat((List<String>) read(unlocks, "$[*].status")).containsExactly("Used");
        assertThat((List<Integer>) read(unlocks, "$[*].learningPathId")).containsExactly(advancedId);
        assertThat(paths.countActiveByStudentId(ana.getId())).isEqualTo(1);
        assertThat(paths.countByStudentId(ana.getId())).isEqualTo(1);

        MvcResult again = declare(HTTP, true);
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) read(again, "$.title")).isEqualTo("AdvancedPathUnlockRequired");
    }

    @Test
    @DisplayName("US05 E6: the advanced path is not counted in the total either")
    void freePlan_withThreePathsInTotal_theAdvancedPathIsNotCounted() throws Exception {
        giveCredits(200);
        redeemAdvancedPath();
        assertThat(declare(REST_AND_JWT, true).getResponse().getStatus()).isEqualTo(201);

        // The free plan allows 3 paths in total: the advanced one leaves room for three regular ones.
        for (String goal : List.of(SQL, HTTP, DOCKER)) {
            LearningPath created = declareDirectly(goal).value();
            assertThat(created).as(goal).isNotNull();
            commands.handle(new PauseLearningPathCommand(created.getId(), ana.getId()));
        }
        Result<LearningPath> fourth = declareDirectly("quiero aprender Git y control de versiones");
        assertThat(fourth.error()).isEqualTo(LearningPathError.PLAN_LIMIT_REACHED);
        assertThat(fourth.details()).containsEntry("limit", "TotalRoutes").containsEntry("current", 3);
    }

    @Test
    void advancedPath_canBeResumedWhateverTheLimit() throws Exception {
        giveCredits(200);
        redeemAdvancedPath();
        int advancedId = read(declare(REST_AND_JWT, true), "$.id");
        assertThat(call(patch("/api/v1/learning-paths/" + advancedId + "/pause"), null).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(declare(SQL, false).getResponse().getStatus()).isEqualTo(201);

        MvcResult resumed = call(patch("/api/v1/learning-paths/" + advancedId + "/resume"), null);

        assertThat(resumed.getResponse().getStatus()).isEqualTo(200);
        assertThat(paths.findByStudentId(ana.getId()).stream().filter(LearningPath::isActive)).hasSize(2);
    }

    @Test
    @DisplayName("US05 E4: back on the free plan, the advanced path stays available and is not paused")
    void expiredSubscription_keepsTheAdvancedPathActive() throws Exception {
        FakePaymentGateway gateway = (FakePaymentGateway) paymentGateway;
        gateway.willAnswer(ana.getId(), FakePaymentGateway.active("GPA." + ana.getId()));
        assertThat(subscriptions.handle(new CreateSubscriptionCommand(ana.getId(), null)).isSuccess()).isTrue();
        giveCredits(200);
        redeemAdvancedPath();
        int advancedId = read(declare(REST_AND_JWT, true), "$.id");
        LearningPath older = declareDirectly(SQL).value();
        LearningPath recent = declareDirectly(HTTP).value();
        execute("UPDATE learning_paths SET last_progress_at = now() - interval '9 days' WHERE id = " + older.getId());
        execute("UPDATE learning_paths SET last_progress_at = now() - interval '9 days' WHERE id = " + advancedId);

        gateway.willAnswer(ana.getId(), PurchaseVerification.inactive());
        subscriptions.handle(new ProcessRevenueCatEventCommand("evt-expiration-adv", "EXPIRATION",
                String.valueOf(ana.getId()), String.valueOf(ana.getId()), List.of(), "PRODUCTION"));

        assertThat(queryString("SELECT status FROM subscriptions")).isEqualTo("Expired");
        assertThat(paths.findById(advancedId).orElseThrow().getStatus()).isEqualTo(PathStatus.ACTIVE);
        assertThat(paths.findById(recent.getId()).orElseThrow().getStatus()).isEqualTo(PathStatus.ACTIVE);
        assertThat(paths.findById(older.getId()).orElseThrow().getStatus()).isEqualTo(PathStatus.PAUSED);
    }

    @Test
    void aRedemptionWhoseEventWasLost_isGrantedWhenTheUnlocksAreRead() throws Exception {
        int walletId = walletRepository.save(new Wallet(ana.getId())).getId();
        // A redemption whose event never reached Learning Path Engine (or one made before the benefit was delivered,
        // which the migration recognizes by its description).
        execute("INSERT INTO credit_transactions (wallet_id, amount, type, description, created_at, redemption_item) "
                + "VALUES (" + walletId + ", 200, 'Redeemed', 'Redeemed: advanced path unlock', now(), "
                + "'AdvancedPathUnlock')");

        MvcResult unlocks = call(get("/api/v1/advanced-path-unlocks"), null);

        assertThat((List<String>) read(unlocks, "$[*].status")).containsExactly("Available");
        // Reading them again grants nothing new.
        assertThat((List<Integer>) read(call(get("/api/v1/advanced-path-unlocks"), null), "$[*].id"))
                .isEqualTo(read(unlocks, "$[*].id"));
        assertThat(queryString("SELECT count(*) FROM advanced_path_unlocks")).isEqualTo("1");
    }

    @Test
    void aContributionCertificate_grantsNoAdvancedPath() throws Exception {
        giveCredits(120);

        MvcResult result = call(post("/api/v1/credit-transactions/redeem"), "{\"item\":\"ContributionCertificate\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat((List<?>) read(call(get("/api/v1/advanced-path-unlocks"), null), "$")).isEmpty();
    }
}
