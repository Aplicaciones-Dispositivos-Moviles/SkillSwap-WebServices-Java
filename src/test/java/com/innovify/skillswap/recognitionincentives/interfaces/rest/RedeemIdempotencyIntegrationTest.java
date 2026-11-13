package com.innovify.skillswap.recognitionincentives.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * POST /api/v1/credit-transactions/redeem with the optional Idempotency-Key header: a retry of the same attempt
 * answers the stored movement instead of charging again.
 */
class RedeemIdempotencyIntegrationTest extends PostgresIntegrationTest {

    private static final String REDEEM = "/api/v1/credit-transactions/redeem";
    private static final String WALLETS = "/api/v1/wallets/";
    private static final String HEADER = "Idempotency-Key";
    private static final String SKILL = "networking-basics";
    private static final String KEY = "7f3c2a9e-1b4d-4c8e-9f60-2d5a7b1e0c33";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private DomainEventPublisher publisher;

    private MockMvc mockMvc;
    private int nextCaseId;
    private User ana;
    private User bob;
    private User eva;
    private String bobToken;
    private String evaToken;

    @BeforeEach
    void setUp() {
        nextCaseId = 5000;
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        eva = userRepository.save(TestData.newUser("eva", "eva@upc.edu.pe", UserRole.STUDENT));
        publisher.publish(new UserRegistered(ana.getId(), UserRole.STUDENT));
        publisher.publish(new UserRegistered(bob.getId(), UserRole.STUDENT));
        publisher.publish(new UserRegistered(eva.getId(), UserRole.STUDENT));
        bobToken = tokenGenerator.generateToken(bob);
        evaToken = tokenGenerator.generateToken(eva);
    }

    // ---------- Helpers ----------

    /** The verifier resolves that many mini-project cases: 40 SkillCredits each. */
    private void earnForty(User verifier, int count) {
        for (int i = 0; i < count; i++) {
            publisher.publish(new VerificationCaseResolved(nextCaseId++, ana.getId(), verifier.getId(), 1,
                    SKILL, CaseType.MINI_PROJECT, ReviewDecision.APPROVED, null));
        }
    }

    private MvcResult redeem(String token, String item, String key) throws Exception {
        var request = post(REDEEM).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"item\":\"" + item + "\"}");
        if (key != null) {
            request.header(HEADER, key);
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

    private int balance(String token, User owner) throws Exception {
        MvcResult wallet = mockMvc.perform(get(WALLETS + owner.getId()).header("Authorization", "Bearer " + token))
                .andReturn();
        return read(wallet, "$.balance");
    }

    private int movements(String token, User owner) throws Exception {
        MvcResult history = mockMvc.perform(get(WALLETS + owner.getId() + "/transactions")
                .header("Authorization", "Bearer " + token)).andReturn();
        List<Object> items = read(history, "$");
        return items.size();
    }

    private int storedKeys() throws Exception {
        return Integer.parseInt(queryString("SELECT count(*) FROM redemption_idempotency_keys"));
    }

    // ---------- Without the header ----------

    @Test
    void redeem_withoutTheHeader_chargesEveryCallAndStoresNoKey() throws Exception {
        earnForty(bob, 16);

        MvcResult first = redeem(bobToken, "ContributionCertificate", null);
        MvcResult second = redeem(bobToken, "ContributionCertificate", null);

        assertThat(status(first)).isEqualTo(201);
        assertThat(status(second)).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Location")).isEqualTo(WALLETS + bob.getId() + "/transactions");
        assertThat((int) read(second, "$.id")).isNotEqualTo((int) read(first, "$.id"));
        assertThat((String) read(first, "$.type")).isEqualTo("Redeemed");
        assertThat((int) read(first, "$.amount")).isEqualTo(300);
        assertThat(balance(bobToken, bob)).isEqualTo(40);
        assertThat(storedKeys()).isZero();
    }

    // ---------- With the header ----------

    @Test
    void redeem_withANewKey_returns201ChargesOnceAndStoresTheKey() throws Exception {
        earnForty(bob, 8);

        MvcResult result = redeem(bobToken, "ContributionCertificate", KEY);

        assertThat(status(result)).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(WALLETS + bob.getId() + "/transactions");
        assertThat((String) read(result, "$.type")).isEqualTo("Redeemed");
        assertThat((int) read(result, "$.amount")).isEqualTo(300);
        assertThat(balance(bobToken, bob)).isEqualTo(20);
        assertThat(storedKeys()).isEqualTo(1);
        assertThat(queryString("SELECT credit_transaction_id FROM redemption_idempotency_keys WHERE user_id = "
                + bob.getId() + " AND idempotency_key = '" + KEY + "'"))
                .isEqualTo(String.valueOf((int) read(result, "$.id")));
    }

    @Test
    void redeem_retriedWithTheSameKey_returnsTheStoredMovementWithoutChargingAgain() throws Exception {
        earnForty(bob, 16);
        MvcResult first = redeem(bobToken, "ContributionCertificate", KEY);
        int movementsAfterFirst = movements(bobToken, bob);

        MvcResult retry = redeem(bobToken, "ContributionCertificate", KEY);

        assertThat(status(retry)).isEqualTo(201);
        assertThat(retry.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(body(retry)).isEqualTo(body(first));
        assertThat(balance(bobToken, bob)).isEqualTo(340);
        assertThat(movements(bobToken, bob)).isEqualTo(movementsAfterFirst);
        assertThat(storedKeys()).isEqualTo(1);
    }

    @Test
    void redeem_retriedWithTheSameKeyAndAnotherBenefit_stillReturnsTheStoredMovement() throws Exception {
        earnForty(bob, 16);
        MvcResult first = redeem(bobToken, "ContributionCertificate", KEY);

        MvcResult retry = redeem(bobToken, "Badge", KEY);

        assertThat(status(retry)).isEqualTo(201);
        assertThat(body(retry)).isEqualTo(body(first));
        assertThat(balance(bobToken, bob)).isEqualTo(340);
    }

    @Test
    void redeem_theSameKeyFromAnotherUser_isAnIndependentRedemption() throws Exception {
        earnForty(bob, 8);
        earnForty(eva, 8);
        MvcResult bobs = redeem(bobToken, "ContributionCertificate", KEY);

        MvcResult evas = redeem(evaToken, "ContributionCertificate", KEY);

        assertThat(status(evas)).isEqualTo(201);
        assertThat((int) read(evas, "$.id")).isNotEqualTo((int) read(bobs, "$.id"));
        assertThat((int) read(evas, "$.walletId")).isNotEqualTo((int) read(bobs, "$.walletId"));
        assertThat(balance(bobToken, bob)).isEqualTo(20);
        assertThat(balance(evaToken, eva)).isEqualTo(20);
        assertThat(storedKeys()).isEqualTo(2);
    }

    @Test
    void redeem_thatFails_doesNotStoreTheKeySoARetryCanSucceed() throws Exception {
        earnForty(bob, 7);

        MvcResult failed = redeem(bobToken, "ContributionCertificate", KEY);

        assertThat(status(failed)).isEqualTo(409);
        assertThat((String) read(failed, "$.title")).isEqualTo("InsufficientBalance");
        assertThat(storedKeys()).isZero();

        earnForty(bob, 1);
        MvcResult retry = redeem(bobToken, "ContributionCertificate", KEY);

        assertThat(status(retry)).isEqualTo(201);
        assertThat(balance(bobToken, bob)).isEqualTo(20);
        assertThat(storedKeys()).isEqualTo(1);
    }

    @Test
    void redeem_withABlankKey_returns400AndChargesNothing() throws Exception {
        earnForty(bob, 8);

        MvcResult result = redeem(bobToken, "ContributionCertificate", "   ");

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.title")).isEqualTo("InvalidIdempotencyKey");
        assertThat(balance(bobToken, bob)).isEqualTo(320);
        assertThat(storedKeys()).isZero();
    }

    @Test
    void redeem_withAKeyLongerThan64Characters_returns400AndChargesNothing() throws Exception {
        earnForty(bob, 8);

        MvcResult result = redeem(bobToken, "ContributionCertificate", "k".repeat(65));

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.title")).isEqualTo("InvalidIdempotencyKey");
        assertThat(balance(bobToken, bob)).isEqualTo(320);
    }

    @Test
    void redeem_withAKeyOf64Characters_isAccepted() throws Exception {
        earnForty(bob, 8);

        assertThat(status(redeem(bobToken, "ContributionCertificate", "k".repeat(64)))).isEqualTo(201);
    }

    @Test
    void twoSimultaneousRedemptionsWithTheSameKey_chargeOnlyOnceAndAnswerTheSameMovement() throws Exception {
        earnForty(bob, 16);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        List<MvcResult> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return redeem(bobToken, "ContributionCertificate", KEY);
                }));
            }
            start.countDown();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(results).allSatisfy(result -> assertThat(status(result)).isEqualTo(201));
        assertThat(body(results.get(1))).isEqualTo(body(results.get(0)));
        assertThat(balance(bobToken, bob)).isEqualTo(340);
        assertThat(storedKeys()).isEqualTo(1);
    }
}
