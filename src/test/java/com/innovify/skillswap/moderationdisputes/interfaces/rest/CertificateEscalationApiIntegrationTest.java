package com.innovify.skillswap.moderationdisputes.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.moderationdisputes.application.commandservices.DisputeCommandService;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.AssignPendingDisputesCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.EscalateCertificateReviewCommand;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US13 (escenario 3) and US14 (escenario 3) end to end: a suspicious certificate is escalated to a Verificador
 * senior through Moderation &amp; Disputes, who reviews and resolves it. Real security, services and PostgreSQL.
 */
class CertificateEscalationApiIntegrationTest extends PostgresIntegrationTest {

    private static final String CERTIFICATES = "/api/v1/certificates";
    private static final String DISPUTES = "/api/v1/disputes";

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
    private DisputeCommandService disputeCommandService;

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
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT)
                .updateFullName("Ana María Pérez García"));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        carla = userRepository.save(TestData.newUser("carla", "carla@upc.edu.pe", UserRole.STUDENT));
        dan = userRepository.save(TestData.newUser("dan", "dan@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
        carlaToken = tokenGenerator.generateToken(carla);
        danToken = tokenGenerator.generateToken(dan);
    }

    // ---------- Helpers ----------

    private static byte[] jpeg(String seed) {
        byte[] body = seed.getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[4 + body.length];
        file[0] = (byte) 0xFF;
        file[1] = (byte) 0xD8;
        file[2] = (byte) 0xFF;
        file[3] = (byte) 0xE0;
        System.arraycopy(body, 0, file, 4, body.length);
        return file;
    }

    private MvcResult upload(String token, byte[] file, String holderName) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(CERTIFICATES);
        request.file(new MockMultipartFile("file", "certificate", "image/jpeg", file));
        Map.of("holderName", holderName, "institutionName", "Coursera", "courseName", "Backend with Spring",
                "issueDate", "2025-03-10").forEach(request::param);
        request.header("Authorization", "Bearer " + token);
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(status(result)).as(body(result)).isEqualTo(201);
        return result;
    }

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

    /** An enabled, available verifier. */
    private void enrollVerifier(User user) {
        profileRepository.save(new VerifierProfile(user.getId(), "networking-basics"));
    }

    /** A Verificador senior: Gold rank (100 resolved cases) and a reliability of 100. */
    private void makeSenior(User user) {
        enrollVerifier(user);
        VerifierReliability reliability = new VerifierReliability(user.getId());
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(calculator);
        }
        reliabilityRepository.save(reliability);
    }

    /** Bob registers a file and Ana registers the very same file afterwards. */
    private int uploadFileAlreadyRegisteredByAnotherStudent() throws Exception {
        upload(bobToken, jpeg("shared-file"), "Bob");
        return read(upload(anaToken, jpeg("shared-file"), "Ana Pérez"), "$.id");
    }

    private int onlyDisputeId() throws Exception {
        String id = queryString("SELECT id FROM disputes");
        assertThat(id).isNotNull();
        return Integer.parseInt(id);
    }

    // ---------- US14 escenario 3: certificate registered by another user ----------

    @Test
    @DisplayName("US14 E3: a file another student registered is suspicious and goes to a Verificador senior")
    void upload_fileOfAnotherStudent_isSuspiciousAndEscalatedToASenior() throws Exception {
        makeSenior(carla);
        enrollVerifier(dan);

        int certificateId = uploadFileAlreadyRegisteredByAnotherStudent();

        MvcResult certificate = call(get(CERTIFICATES + "/" + certificateId), anaToken, null);
        assertThat((String) read(certificate, "$.status")).isEqualTo("Suspicious");
        assertThat(queryString("SELECT source_type || '|' || source_reference_id || '|' || assigned_verifier_user_id"
                + " || '|' || assigned_to_senior || '|' || status FROM disputes"))
                .isEqualTo("CertificateReview|" + certificateId + "|" + carla.getId() + "|true|Pending");
        assertThat(queryString("SELECT reason FROM disputes")).contains("DuplicateFile");
    }

    @Test
    @DisplayName("US14 E3: the owner of the certificate never reviews it, even being a senior")
    void upload_byASenior_goesToAnotherSenior() throws Exception {
        makeSenior(ana);
        makeSenior(carla);

        uploadFileAlreadyRegisteredByAnotherStudent();

        assertThat(queryString("SELECT assigned_verifier_user_id FROM disputes")).isEqualTo(String.valueOf(carla.getId()));
    }

    @Test
    @DisplayName("US14 E3: without a senior, an enabled verifier reviews it and the dispute records the fallback")
    void upload_withoutASenior_goesToAnotherVerifier() throws Exception {
        enrollVerifier(ana);
        enrollVerifier(dan);

        uploadFileAlreadyRegisteredByAnotherStudent();

        assertThat(queryString("SELECT assigned_verifier_user_id || '|' || assigned_to_senior FROM disputes"))
                .isEqualTo(dan.getId() + "|false");
    }

    @Test
    @DisplayName("US14 E3: without any verifier the dispute waits and is assigned once a verifier is available")
    void upload_withoutVerifiers_waitsUntilOneIsAvailable() throws Exception {
        uploadFileAlreadyRegisteredByAnotherStudent();
        assertThat(queryString("SELECT assigned_verifier_user_id FROM disputes")).isNull();

        enrollVerifier(dan);
        var assigned = disputeCommandService.handle(new AssignPendingDisputesCommand());

        assertThat(assigned.value()).isEqualTo(1);
        assertThat(queryString("SELECT assigned_verifier_user_id FROM disputes")).isEqualTo(String.valueOf(dan.getId()));
        assertThat(disputeCommandService.handle(new AssignPendingDisputesCommand()).value()).isZero();
    }

    @Test
    void escalate_theSameCertificateTwice_opensASingleDispute() throws Exception {
        makeSenior(carla);
        int certificateId = uploadFileAlreadyRegisteredByAnotherStudent();

        var again = disputeCommandService.handle(new EscalateCertificateReviewCommand(certificateId, ana.getId(),
                List.of("DuplicateFile")));

        assertThat(again.isSuccess()).isTrue();
        assertThat(queryString("SELECT count(*) FROM disputes")).isEqualTo("1");
    }

    @Test
    void upload_anOriginalFile_isNotEscalated() throws Exception {
        makeSenior(carla);

        MvcResult result = upload(anaToken, jpeg("original"), "Ana Pérez García");

        assertThat((String) read(result, "$.status")).isEqualTo("Unverified");
        assertThat(queryString("SELECT count(*) FROM disputes")).isEqualTo("0");
    }

    // ---------- US13 escenario 3: holder different from the user ----------

    @Test
    @DisplayName("US13 E3: a holder who is not the registered student makes the certificate suspicious and escalated")
    void upload_withAnotherHolder_isSuspiciousAndEscalated() throws Exception {
        makeSenior(carla);

        MvcResult result = upload(anaToken, jpeg("someone-else"), "Luis Gómez Torres");

        assertThat((String) read(result, "$.status")).isEqualTo("Suspicious");
        assertThat((Boolean) read(result, "$.holderNameMismatch")).isTrue();
        assertThat((String) read(result, "$.riskLevel")).isEqualTo("HighRisk");
        assertThat(queryString("SELECT reason FROM disputes")).isEqualTo("HolderNameMismatch");
        assertThat(queryString("SELECT assigned_verifier_user_id FROM disputes")).isEqualTo(String.valueOf(carla.getId()));
    }

    @Test
    @DisplayName("US13 E3: the holder matches ignoring accents, case, order and a missing middle name")
    void upload_withTheHolderWrittenDifferently_isNotSuspicious() throws Exception {
        MvcResult result = upload(anaToken, jpeg("same-person"), "PEREZ GARCIA, ANA");

        assertThat((String) read(result, "$.status")).isEqualTo("Unverified");
        assertThat((Boolean) read(result, "$.holderNameMismatch")).isFalse();
        assertThat(queryString("SELECT count(*) FROM disputes")).isEqualTo("0");
    }

    @Test
    void upload_byAStudentWithoutARegisteredName_isNotCompared() throws Exception {
        MvcResult result = upload(bobToken, jpeg("bob-file"), "Luis Gómez");

        assertThat((String) read(result, "$.status")).isEqualTo("Unverified");
        assertThat((Boolean) read(result, "$.holderNameMismatch")).isFalse();
    }

    @Test
    void updateFullName_thenUploadAnotherHolder_isSuspicious() throws Exception {
        MvcResult updated = call(patch("/api/v1/users/" + bob.getId() + "/full-name"), bobToken,
                "{\"fullName\":\"  Roberto   Díaz \"}");
        assertThat(status(updated)).isEqualTo(200);
        assertThat((String) read(updated, "$.fullName")).isEqualTo("Roberto Díaz");

        MvcResult result = upload(bobToken, jpeg("bob-other"), "Luis Gómez");
        assertThat((String) read(result, "$.status")).isEqualTo("Suspicious");
    }

    @Test
    void updateFullName_ofAnotherUser_returns403() throws Exception {
        MvcResult result = call(patch("/api/v1/users/" + ana.getId() + "/full-name"), bobToken,
                "{\"fullName\":\"Bob\"}");
        assertThat(status(result)).isEqualTo(403);
    }

    // ---------- The reviewer sees and resolves the suspicious certificate ----------

    @Test
    void listAndEvidence_onlyTheReviewerSeesTheDispute() throws Exception {
        makeSenior(carla);
        enrollVerifier(dan);
        int certificateId = uploadFileAlreadyRegisteredByAnotherStudent();
        int disputeId = onlyDisputeId();

        MvcResult list = call(get(DISPUTES), carlaToken, null);
        assertThat(status(list)).isEqualTo(200);
        assertThat((List<Integer>) read(list, "$[*].id")).containsExactly(disputeId);
        assertThat((String) read(list, "$[0].sourceType")).isEqualTo("CertificateReview");
        assertThat((Boolean) read(list, "$[0].assignedToSenior")).isTrue();
        assertThat(body(list)).doesNotContain("respondent");

        MvcResult evidence = call(get(DISPUTES + "/" + disputeId + "/evidence"), carlaToken, null);
        assertThat(status(evidence)).isEqualTo(200);
        assertThat((Integer) read(evidence, "$.certificate.id")).isEqualTo(certificateId);
        assertThat((String) read(evidence, "$.certificate.status")).isEqualTo("Suspicious");
        assertThat((String) read(evidence, "$.certificate.holderName")).isEqualTo("Ana Pérez");
        assertThat((String) read(evidence, "$.certificate.fileUrl")).isNotBlank();

        assertThat((List<?>) read(call(get(DISPUTES), danToken, null), "$")).isEmpty();
        assertThat(status(call(get(DISPUTES + "/" + disputeId + "/evidence"), danToken, null))).isEqualTo(403);
        assertThat(status(call(get(DISPUTES + "/" + disputeId + "/evidence"), anaToken, null))).isEqualTo(403);
        assertThat(status(call(get(DISPUTES), bobToken, null))).isEqualTo(403);
        assertThat(status(call(get(DISPUTES + "/999/evidence"), carlaToken, null))).isEqualTo(404);
        assertThat(status(call(get(DISPUTES).param("status", "Other"), carlaToken, null))).isEqualTo(400);
    }

    @Test
    void resolve_upheld_verifiesTheCertificate() throws Exception {
        makeSenior(carla);
        int certificateId = uploadFileAlreadyRegisteredByAnotherStudent();
        int disputeId = onlyDisputeId();

        MvcResult resolved = call(patch(DISPUTES + "/" + disputeId + "/resolve"), carlaToken,
                "{\"outcome\":\"Upheld\",\"coordinatorNotes\":\"The issuer confirmed the certificate.\"}");

        assertThat(status(resolved)).as(body(resolved)).isEqualTo(200);
        assertThat((String) read(resolved, "$.status")).isEqualTo("Resolved");
        assertThat((String) read(resolved, "$.outcome")).isEqualTo("Upheld");
        MvcResult certificate = call(get(CERTIFICATES + "/" + certificateId), anaToken, null);
        assertThat((String) read(certificate, "$.status")).isEqualTo("Verified");
        assertThat((String) read(certificate, "$.verificationMethod")).isEqualTo("Manual");

        MvcResult resolvedList = call(get(DISPUTES).param("status", "Resolved"), carlaToken, null);
        assertThat((List<Integer>) read(resolvedList, "$[*].id")).containsExactly(disputeId);
        assertThat((List<?>) read(call(get(DISPUTES), carlaToken, null), "$")).isEmpty();
    }

    @Test
    void resolve_overturned_rejectsTheCertificate() throws Exception {
        makeSenior(carla);
        int certificateId = uploadFileAlreadyRegisteredByAnotherStudent();
        int disputeId = onlyDisputeId();

        MvcResult resolved = call(patch(DISPUTES + "/" + disputeId + "/resolve"), carlaToken,
                "{\"outcome\":\"Overturned\",\"coordinatorNotes\":\"Same file as another student.\"}");

        assertThat(status(resolved)).isEqualTo(200);
        assertThat((String) read(call(get(CERTIFICATES + "/" + certificateId), anaToken, null), "$.status"))
                .isEqualTo("Rejected");
    }

    @Test
    void resolve_withoutObservations_returns400AndChangesNothing() throws Exception {
        makeSenior(carla);
        int certificateId = uploadFileAlreadyRegisteredByAnotherStudent();
        int disputeId = onlyDisputeId();

        MvcResult result = call(patch(DISPUTES + "/" + disputeId + "/resolve"), carlaToken,
                "{\"outcome\":\"Upheld\",\"coordinatorNotes\":\"   \"}");

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.title")).isEqualTo("CoordinatorNotesRequired");
        assertThat((String) read(call(get(CERTIFICATES + "/" + certificateId), anaToken, null), "$.status"))
                .isEqualTo("Suspicious");
    }

    @Test
    void resolve_withAnOutcomeThatDoesNotApply_returns400() throws Exception {
        makeSenior(carla);
        uploadFileAlreadyRegisteredByAnotherStudent();
        int disputeId = onlyDisputeId();

        MvcResult result = call(patch(DISPUTES + "/" + disputeId + "/resolve"), carlaToken,
                "{\"outcome\":\"Sanctioned\",\"coordinatorNotes\":\"Fraud\"}");

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.title")).isEqualTo("InvalidOutcome");
    }

    @Test
    void resolve_byAnotherUser_returns403AndTwice_returns409() throws Exception {
        makeSenior(carla);
        enrollVerifier(dan);
        uploadFileAlreadyRegisteredByAnotherStudent();
        int disputeId = onlyDisputeId();
        String decision = "{\"outcome\":\"Upheld\",\"coordinatorNotes\":\"Legitimate.\"}";

        assertThat(status(call(patch(DISPUTES + "/" + disputeId + "/resolve"), danToken, decision))).isEqualTo(403);
        assertThat(status(call(patch(DISPUTES + "/" + disputeId + "/resolve"), anaToken, decision))).isEqualTo(403);
        assertThat(status(call(patch(DISPUTES + "/" + disputeId + "/resolve"), carlaToken, decision))).isEqualTo(200);

        MvcResult again = call(patch(DISPUTES + "/" + disputeId + "/resolve"), carlaToken, decision);
        assertThat(status(again)).isEqualTo(409);
        assertThat((String) read(again, "$.title")).isEqualTo("DisputeAlreadyResolved");
        assertThat(status(call(patch(DISPUTES + "/999/resolve"), carlaToken, decision))).isEqualTo(404);
    }
}
