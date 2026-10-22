package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
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
 * T61 end to end: a Verificador senior suspends a verifier with a low reliability (their cases in progress go to
 * other verifiers) and reinstates them. Real security, services and PostgreSQL.
 */
class VerifierSuspensionIntegrationTest extends PostgresIntegrationTest {

    private static final String PROFILES = "/api/v1/verifier-profiles";
    private static final String SKILL = "networking-basics";
    private static final String JUSTIFICATION = "{\"justification\":\"Too many overturned decisions\"}";

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

    private MockMvc mockMvc;
    private User ana;
    private User weak;
    private User other;
    private User senior;
    private String anaToken;
    private String weakToken;
    private String otherToken;
    private String seniorToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        weak = userRepository.save(TestData.newUser("wendy", "wendy@upc.edu.pe", UserRole.STUDENT));
        other = userRepository.save(TestData.newUser("omar", "omar@upc.edu.pe", UserRole.STUDENT));
        senior = userRepository.save(TestData.newUser("sara", "sara@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        weakToken = tokenGenerator.generateToken(weak);
        otherToken = tokenGenerator.generateToken(other);
        seniorToken = tokenGenerator.generateToken(senior);

        // A Verificador senior: an enabled verifier with the Gold rank and a reliability of 100.
        profileRepository.save(new VerifierProfile(senior.getId(), "other-skill"));
        VerifierReliability reliability = new VerifierReliability(senior.getId());
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(calculator);
        }
        reliabilityRepository.save(reliability);

        // A verifier with a reliability of 40, and another one with a record that is not low.
        profileRepository.save(new VerifierProfile(weak.getId(), SKILL));
        reliabilityRepository.save(new VerifierReliability(weak.getId()));
        execute("UPDATE verifier_reliabilities SET score = 40 WHERE verifier_user_id = " + weak.getId());
        profileRepository.save(new VerifierProfile(other.getId(), SKILL));
        reliabilityRepository.save(new VerifierReliability(other.getId()));
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

    private MvcResult suspend(String token, User target, String json) throws Exception {
        return call(post(PROFILES + "/" + target.getId() + "/suspension"), token, json);
    }

    private MvcResult reinstate(String token, User target) throws Exception {
        return call(delete(PROFILES + "/" + target.getId() + "/suspension"), token, null);
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

    private int caseAssignedTo(User verifier) {
        return caseRepository.save(new VerificationCase(1, ana.getId(), 1, SKILL, CaseType.QUIZ,
                ReviewDeadline.hours(48)).assignVerifier(verifier.getId())).getId();
    }

    private String caseRow(int caseId) throws Exception {
        return queryString("SELECT status || '|' || coalesce(verifier_user_id::text, 'none') || '|' || "
                + "(deadline_missed_at IS NULL) FROM verification_cases WHERE id = " + caseId);
    }

    // ---------- Suspend ----------

    @Test
    @DisplayName("T61: a senior suspends a verifier with a low reliability and their open cases go to another verifier")
    void suspend_asASenior_suspendsAndReassignsTheOpenCases() throws Exception {
        int caseId = caseAssignedTo(weak);

        MvcResult result = suspend(seniorToken, weak, JUSTIFICATION);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(this.<Integer>read(result, "$.verifierUserId")).isEqualTo(weak.getId());
        assertThat(this.<Boolean>read(result, "$.suspended")).isTrue();
        assertThat(this.<String>read(result, "$.suspensionReason")).isEqualTo("Too many overturned decisions");
        assertThat(this.<String>read(result, "$.suspendedAt")).isNotBlank();
        assertThat(caseRow(caseId)).isEqualTo("Assigned|" + other.getId() + "|true");
        assertThat(queryString("SELECT suspended_by FROM verifier_profiles WHERE verifier_user_id = "
                + weak.getId())).isEqualTo(String.valueOf(senior.getId()));
    }

    @Test
    void suspend_withoutAnotherVerifier_leavesTheCasePending() throws Exception {
        execute("DELETE FROM verifier_profiles WHERE verifier_user_id = " + other.getId());
        int caseId = caseAssignedTo(weak);

        assertThat(status(suspend(seniorToken, weak, JUSTIFICATION))).isEqualTo(200);

        assertThat(caseRow(caseId)).isEqualTo("Pending|none|true");
    }

    @Test
    void suspendedVerifier_seesTheSuspensionInTheirProfile() throws Exception {
        suspend(seniorToken, weak, JUSTIFICATION);

        MvcResult result = call(get(PROFILES + "/me"), weakToken, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat(this.<Boolean>read(result, "$.suspended")).isTrue();
        assertThat(this.<String>read(result, "$.suspensionReason")).isEqualTo("Too many overturned decisions");
    }

    @Test
    void suspend_byWhoIsNotASenior_returns403() throws Exception {
        for (String token : new String[] {anaToken, otherToken, weakToken}) {
            MvcResult result = suspend(token, weak, JUSTIFICATION);
            assertThat(status(result)).isEqualTo(403);
            assertThat(title(result)).isEqualTo("NotSeniorVerifier");
        }
        assertThat(queryString("SELECT suspended FROM verifier_profiles WHERE verifier_user_id = " + weak.getId()))
                .isEqualTo("f");
    }

    @Test
    void suspend_withoutToken_returns401() throws Exception {
        assertThat(status(suspend(null, weak, JUSTIFICATION))).isEqualTo(401);
    }

    @Test
    void suspend_withoutJustification_returns400() throws Exception {
        for (String json : new String[] {null, "{}", "{\"justification\":\"   \"}"}) {
            MvcResult result = suspend(seniorToken, weak, json);
            assertThat(status(result)).as(String.valueOf(json)).isEqualTo(400);
            assertThat(title(result)).isEqualTo("JustificationRequired");
        }
    }

    @Test
    void suspend_withATooLongJustification_returns400() throws Exception {
        MvcResult result = suspend(seniorToken, weak, "{\"justification\":\"" + "a".repeat(2001) + "\"}");

        assertThat(status(result)).isEqualTo(400);
        assertThat(title(result)).isEqualTo("JustificationTooLong");
    }

    @Test
    void suspend_aVerifierWithoutProfile_returns404() throws Exception {
        MvcResult result = call(post(PROFILES + "/9999/suspension"), seniorToken, JUSTIFICATION);

        assertThat(status(result)).isEqualTo(404);
        assertThat(title(result)).isEqualTo("VerifierProfileNotFound");
    }

    @Test
    void suspend_oneself_returns409() throws Exception {
        MvcResult result = suspend(seniorToken, senior, JUSTIFICATION);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("CannotSuspendSelf");
    }

    @Test
    void suspend_aVerifierThatIsNotLowReliability_returns409() throws Exception {
        MvcResult result = suspend(seniorToken, other, JUSTIFICATION);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("NotLowReliability");
    }

    @Test
    void suspend_aSuspendedVerifier_returns409() throws Exception {
        suspend(seniorToken, weak, JUSTIFICATION);

        MvcResult result = suspend(seniorToken, weak, JUSTIFICATION);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("AlreadySuspended");
    }

    // ---------- Reinstate ----------

    @Test
    @DisplayName("T61: a senior reinstates a suspended verifier, who takes the waiting cases again")
    void reinstate_asASenior_reinstatesAndAssignsTheWaitingCases() throws Exception {
        execute("DELETE FROM verifier_profiles WHERE verifier_user_id = " + other.getId());
        int caseId = caseAssignedTo(weak);
        suspend(seniorToken, weak, JUSTIFICATION);
        assertThat(caseRow(caseId)).isEqualTo("Pending|none|true");

        MvcResult result = reinstate(seniorToken, weak);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(this.<Boolean>read(result, "$.suspended")).isFalse();
        assertThat(this.<Object>read(result, "$.suspensionReason")).isNull();
        assertThat(caseRow(caseId)).isEqualTo("Assigned|" + weak.getId() + "|true");
    }

    @Test
    void reinstate_aVerifierThatIsNotSuspended_returns409() throws Exception {
        MvcResult result = reinstate(seniorToken, weak);

        assertThat(status(result)).isEqualTo(409);
        assertThat(title(result)).isEqualTo("NotSuspended");
    }

    @Test
    void reinstate_byWhoIsNotASenior_returns403() throws Exception {
        suspend(seniorToken, weak, JUSTIFICATION);

        MvcResult result = reinstate(otherToken, weak);

        assertThat(status(result)).isEqualTo(403);
        assertThat(title(result)).isEqualTo("NotSeniorVerifier");
    }

    @Test
    void reinstate_withoutProfile_returns404() throws Exception {
        assertThat(status(call(delete(PROFILES + "/9999/suspension"), seniorToken, null))).isEqualTo(404);
    }

    @Test
    void reinstate_withoutToken_returns401() throws Exception {
        assertThat(status(reinstate(null, weak))).isEqualTo(401);
    }
}
