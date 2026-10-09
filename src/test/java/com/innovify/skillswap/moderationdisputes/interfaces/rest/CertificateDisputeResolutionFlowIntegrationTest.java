package com.innovify.skillswap.moderationdisputes.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.fakes.FakePushNotificationSender;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.shared.infrastructure.json.Json;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The resolution of a certificate dispute across the bounded contexts, end to end against PostgreSQL: a suspicious
 * certificate is escalated to a Verificador senior (Moderation &amp; Disputes, US13/US14), whose decision moves the
 * certificate to its final status (Credential Verification), notifies the student on their device with the
 * resolution notes as the reason of a rejection (IAM, US16) and, when the certificate is upheld as authentic,
 * completes the node of the learning path it covers (Learning Path Engine, US09).
 */
class CertificateDisputeResolutionFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String CERTIFICATES = "/api/v1/certificates";
    private static final String DISPUTES = "/api/v1/disputes";
    private static final String PATHS = "/api/v1/learning-paths";
    private static final String REST_AND_JWT = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String ANA_DEVICE = "fcm-token-of-ana";

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
    private FakePushNotificationSender pushSender;

    private MockMvc mockMvc;
    private User ana;
    private String anaToken;
    private String bobToken;
    private String carlaToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        pushSender.clear();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT).verify());
        User bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT).verify());
        User carla = userRepository.save(TestData.newUser("carla", "carla@upc.edu.pe", UserRole.STUDENT).verify());
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
        carlaToken = tokenGenerator.generateToken(carla);
        makeSenior(carla);

        // Ana granted the notification permission on her phone.
        MvcResult registered = call(put("/api/v1/users/me/device-token"), anaToken,
                Json.write(Map.of("token", ANA_DEVICE)));
        assertThat(status(registered)).isEqualTo(204);
    }

    // ---------- Helpers ----------

    /** A Verificador senior: an enabled verifier with Gold rank (100 resolved cases) and a reliability of 100. */
    private void makeSenior(User user) {
        profileRepository.save(new VerifierProfile(user.getId(), "networking-basics"));
        VerifierReliability reliability = new VerifierReliability(user.getId());
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(calculator);
        }
        reliabilityRepository.save(reliability);
    }

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

    private MvcResult upload(String token, byte[] file, String holderName, String courseName) throws Exception {
        MvcResult result = mockMvc.perform(multipart(CERTIFICATES)
                        .file(new MockMultipartFile("file", "certificate", "image/jpeg", file))
                        .param("holderName", holderName)
                        .param("institutionName", "Coursera")
                        .param("courseName", courseName)
                        .param("issueDate", "2025-03-10")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(status(result)).as(body(result)).isEqualTo(201);
        return result;
    }

    /** Bob registered the file first, so the copy Ana registers is suspicious and escalated to the senior. */
    private int suspiciousCertificateOfAna(String courseName) throws Exception {
        upload(bobToken, jpeg("shared-file"), "Bob", "Other course");
        MvcResult result = upload(anaToken, jpeg("shared-file"), "Ana Pérez", courseName);
        assertThat((String) read(result, "$.status")).isEqualTo("Suspicious");
        return read(result, "$.id");
    }

    private int onlyDisputeId() throws Exception {
        String id = queryString("SELECT id FROM disputes");
        assertThat(id).as("the certificate was escalated").isNotNull();
        return Integer.parseInt(id);
    }

    private MvcResult resolveDispute(int disputeId, String outcome, String notes) throws Exception {
        return call(patch(DISPUTES + "/" + disputeId + "/resolve"), carlaToken,
                Json.write(Map.of("outcome", outcome, "resolutionNotes", notes)));
    }

    private MvcResult declarePath() throws Exception {
        MvcResult path = call(post(PATHS), anaToken, Json.write(Map.of("goal", REST_AND_JWT)));
        assertThat(status(path)).as(body(path)).isEqualTo(201);
        return path;
    }

    private Map<String, Object> node(MvcResult path, String skillTag) throws Exception {
        List<Map<String, Object>> nodes = read(path, "$.nodes[?(@.skillTag=='" + skillTag + "')]");
        assertThat(nodes).as("node " + skillTag).hasSize(1);
        return nodes.get(0);
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

    // ---------- Scenarios ----------

    @Test
    @DisplayName("Upheld: the certificate is Verified, the student is notified and the node it covers is completed")
    void resolveUpheld_verifiesTheCertificateNotifiesTheStudentAndCompletesTheNode() throws Exception {
        assertThat(node(declarePath(), "http-basics")).containsEntry("status", "Locked");
        int certificateId = suspiciousCertificateOfAna("Fundamentos de HTTP");
        assertThat(pushSender.sent()).isEmpty();

        MvcResult resolved = resolveDispute(onlyDisputeId(), "Upheld", "The issuer confirmed the certificate.");

        assertThat(status(resolved)).as(body(resolved)).isEqualTo(200);
        assertThat((String) read(resolved, "$.outcome")).isEqualTo("Upheld");
        assertThat((String) read(resolved, "$.resolutionNotes")).isEqualTo("The issuer confirmed the certificate.");
        assertThat(queryString("SELECT resolution_notes FROM disputes"))
                .isEqualTo("The issuer confirmed the certificate.");

        MvcResult certificate = call(get(CERTIFICATES + "/" + certificateId), anaToken, null);
        assertThat((String) read(certificate, "$.status")).isEqualTo("Verified");

        assertThat(pushSender.sent()).singleElement().satisfies(push -> {
            assertThat(push.deviceToken()).isEqualTo(ANA_DEVICE);
            assertThat(push.notification().title()).isEqualTo("Certificado verificado");
            assertThat(push.notification().body()).contains("Fundamentos de HTTP");
            assertThat(push.notification().data())
                    .containsEntry("certificateId", String.valueOf(certificateId))
                    .containsEntry("status", "Verified");
        });

        MvcResult path = call(get(PATHS + "/" + ana.getId()), anaToken, null);
        assertThat(node(path, "http-basics")).containsEntry("status", "Completed")
                .containsEntry("linkedCertificateId", certificateId)
                .containsEntry("completedByCertificate", true);
        assertThat(node(path, "rest-api-design")).containsEntry("status", "Locked");
    }

    @Test
    @DisplayName("Overturned: the certificate is Rejected and the student is notified with the resolution notes")
    void resolveOverturned_rejectsTheCertificateAndNotifiesTheReason() throws Exception {
        declarePath();
        int certificateId = suspiciousCertificateOfAna("Fundamentos de HTTP");

        MvcResult resolved = resolveDispute(onlyDisputeId(), "Overturned", "Same file as another student.");

        assertThat(status(resolved)).as(body(resolved)).isEqualTo(200);
        MvcResult certificate = call(get(CERTIFICATES + "/" + certificateId), anaToken, null);
        assertThat((String) read(certificate, "$.status")).isEqualTo("Rejected");

        assertThat(pushSender.sent()).singleElement().satisfies(push -> {
            assertThat(push.deviceToken()).isEqualTo(ANA_DEVICE);
            assertThat(push.notification().title()).isEqualTo("Certificado rechazado");
            assertThat(push.notification().body()).endsWith("Motivo: Same file as another student.");
            assertThat(push.notification().data()).containsEntry("status", "Rejected");
        });
        assertThat(queryString("SELECT count(*) FROM path_nodes WHERE status = 'Completed'")).isEqualTo("0");
    }

    @Test
    @DisplayName("Overturned with long notes: the dispute keeps them whole and the push carries an abbreviated reason")
    void resolveOverturned_withNotesLongerThanAPushReason_stillResolvesAndAbbreviatesTheReason() throws Exception {
        int certificateId = suspiciousCertificateOfAna("Fundamentos de HTTP");
        String notes = "Fraud. " + "x".repeat(1500);

        MvcResult resolved = resolveDispute(onlyDisputeId(), "Overturned", notes);

        assertThat(status(resolved)).as(body(resolved)).isEqualTo(200);
        assertThat(queryString("SELECT length(resolution_notes) FROM disputes"))
                .isEqualTo(String.valueOf(notes.length()));
        assertThat((String) read(call(get(CERTIFICATES + "/" + certificateId), anaToken, null), "$.status"))
                .isEqualTo("Rejected");
        assertThat(pushSender.sent()).singleElement().satisfies(push -> {
            String reason = push.notification().body().substring(push.notification().body().indexOf("Fraud."));
            assertThat(reason).hasSize(ResolveCertificateDisputeCommand.MAX_REASON_LENGTH).endsWith("…");
        });
    }
}
