package com.innovify.skillswap.learningpathengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.innovify.skillswap.credentialverification.application.commandservices.CertificateCommandService;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import com.innovify.skillswap.shared.infrastructure.json.Json;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
 * Certificates and learning paths end to end, against PostgreSQL: validated certificates recognized as
 * demonstrated skills (US09, through the CertificateVerified event and the Credential Verification facade), the
 * certificate-skill correspondence (US15, POST /api/v1/path-nodes/{id}/certificate) and new attempts with
 * different questions (US17 escenario 3).
 */
class CertificateEvidenceApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PATHS = "/api/v1/learning-paths";
    private static final String REST_AND_JWT = "quiero aprender a construir APIs REST con autenticación JWT";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private CertificateCommandService certificateCommands;

    @Autowired
    private QuestionGenerationService questionGeneration;

    @Autowired
    private LearningPathCommandService learningPathCommands;

    private MockMvc mockMvc;
    private FakeQuestionGenerationService generator;
    private User ana;
    private User bob;
    private String anaToken;
    private String bobToken;
    private int fileSeed;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        generator = (FakeQuestionGenerationService) questionGeneration;
        generator.reset();

        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
    }

    @AfterEach
    void resetGenerator() {
        generator.reset();
    }

    // ---------- Helpers ----------

    private MvcResult declare(String token, String goal) throws Exception {
        return mockMvc.perform(post(PATHS).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("goal", goal)))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult getPath(String token, int studentId) throws Exception {
        return mockMvc.perform(get(PATHS + "/" + studentId).header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult linkCertificate(String token, int nodeId, Object certificateId) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/path-nodes/" + nodeId + "/certificate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.write(certificateId == null ? Map.of() : Map.of("certificateId", certificateId)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult requestBlueprint(String token, int nodeId) throws Exception {
        return mockMvc.perform(post("/api/v1/path-nodes/" + nodeId + "/assessment-blueprint")
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private byte[] newFile() {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] seed = ("certificate-" + (++fileSeed)).getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[header.length + seed.length];
        System.arraycopy(header, 0, file, 0, header.length);
        System.arraycopy(seed, 0, file, header.length, seed.length);
        return file;
    }

    private MvcResult upload(String token, byte[] file, String courseName, String ocrText) throws Exception {
        return mockMvc.perform(multipart("/api/v1/certificates")
                        .file(new MockMultipartFile("file", "certificate", "image/jpeg", file))
                        .param("holderName", "Ana Perez")
                        .param("institutionName", "Coursera")
                        .param("courseName", courseName)
                        .param("issueDate", "2025-03-10")
                        .param("ocrText", ocrText)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    /** A certificate of Ana that passed the risk check (Unverified): it can be evidence. */
    private int uploadEvidence(String courseName, String ocrText) throws Exception {
        MvcResult result = upload(anaToken, newFile(), courseName, ocrText);
        assertThat(status(result)).isEqualTo(201);
        assertThat(this.<String>read(result, "$.status")).isEqualTo("Unverified");
        return read(result, "$.id");
    }

    /** A certificate of Ana that is suspicious: Bob registered the same file before. */
    private int uploadSuspicious(String courseName) throws Exception {
        byte[] file = newFile();
        assertThat(status(upload(bobToken, file, "Other course", ""))).isEqualTo(201);
        MvcResult result = upload(anaToken, file, courseName, "");
        assertThat(this.<String>read(result, "$.status")).isEqualTo("Suspicious");
        return read(result, "$.id");
    }

    /** A certificate of Ana validated by a verifier, which publishes CertificateVerified. */
    private int validatedCertificate(String courseName) throws Exception {
        int id = uploadSuspicious(courseName);
        assertThat(certificateCommands.handle(new ResolveCertificateDisputeCommand(id, true)).isSuccess()).isTrue();
        return id;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private Map<String, Object> node(MvcResult path, String skillTag) throws Exception {
        List<Map<String, Object>> nodes = read(path, "$.nodes[?(@.skillTag=='" + skillTag + "')]");
        return nodes.get(0);
    }

    private int nodeId(MvcResult path, String skillTag) throws Exception {
        return (Integer) node(path, skillTag).get("id");
    }

    // ---------- US09: validated certificates ----------

    @Test
    @DisplayName("US09 escenario 1: the path registers as completed the node covered by a validated certificate")
    void declare_withAValidatedCertificate_completesAndLinksTheNodeOfItsSkill() throws Exception {
        int certificateId = validatedCertificate("Diseño de APIs REST");

        MvcResult path = declare(anaToken, REST_AND_JWT);

        assertThat(status(path)).isEqualTo(201);
        assertThat(this.<List<String>>read(path, "$.nodes[*].skillTag"))
                .containsExactly("rest-api-design", "authentication-jwt");
        assertThat(node(path, "rest-api-design")).containsEntry("status", "Completed")
                .containsEntry("linkedCertificateId", certificateId)
                .containsEntry("completedByCertificate", true);
        assertThat(node(path, "authentication-jwt")).containsEntry("status", "Available")
                .containsEntry("completedByCertificate", false);
        assertThat(queryString("SELECT count(*) FROM path_nodes WHERE completed_by_certificate")).isEqualTo("1");
    }

    @Test
    void declare_withAnUnverifiedCertificate_onlyLinksItAsEvidence() throws Exception {
        int certificateId = uploadEvidence("Diseño de APIs REST", "");

        MvcResult path = declare(anaToken, REST_AND_JWT);

        assertThat(this.<List<String>>read(path, "$.nodes[*].skillTag")).hasSize(5);
        assertThat(node(path, "rest-api-design")).containsEntry("status", "Locked")
                .containsEntry("linkedCertificateId", certificateId)
                .containsEntry("completedByCertificate", false);
    }

    @Test
    @DisplayName("US09 escenario 2: a certificate validated later completes the pending node it covers")
    void certificateValidatedLater_completesThePendingNodeAndKeepsTheCompletedOnes() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");
        assertThat(learningPathCommands.handle(new CompletePathNodeCommand(networkingId)).isSuccess()).isTrue();

        int certificateId = validatedCertificate("Fundamentos de HTTP");

        MvcResult path = getPath(anaToken, ana.getId());
        assertThat(node(path, "http-basics")).containsEntry("status", "Completed")
                .containsEntry("linkedCertificateId", certificateId)
                .containsEntry("completedByCertificate", true);
        assertThat(node(path, "networking-basics")).containsEntry("status", "Completed")
                .containsEntry("completedByCertificate", false);
        assertThat(node(path, "networking-basics").get("linkedCertificateId")).isNull();
        assertThat(node(path, "rest-api-design")).containsEntry("status", "Locked");
    }

    @Test
    void certificateRejected_changesNothing() throws Exception {
        declare(anaToken, REST_AND_JWT);
        int certificateId = uploadSuspicious("Fundamentos de HTTP");

        certificateCommands.handle(new ResolveCertificateDisputeCommand(certificateId, false));

        assertThat(queryString("SELECT count(*) FROM path_nodes WHERE status = 'Completed'")).isEqualTo("0");
    }

    // ---------- US15: associate a certificate with a node ----------

    @Test
    @DisplayName("US15 escenario 1: a certificate that covers the skill is linked and enables the assessment")
    void link_aCertificateThatCoversTheSkill_returns200AndLinksIt() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");
        int certificateId = uploadEvidence("Redes de computadoras", "");

        MvcResult result = linkCertificate(anaToken, networkingId, certificateId);

        assertThat(status(result)).isEqualTo(200);
        assertThat(this.<Integer>read(result, "$.pathNodeId")).isEqualTo(networkingId);
        assertThat(this.<Integer>read(result, "$.certificateId")).isEqualTo(certificateId);
        assertThat(this.<Double>read(result, "$.affinity")).isEqualTo(1.0);
        assertThat(this.<Double>read(result, "$.threshold")).isEqualTo(0.7);
        assertThat(this.<Boolean>read(result, "$.assessmentEnabled")).isTrue();
        assertThat(this.<Integer>read(result, "$.node.linkedCertificateId")).isEqualTo(certificateId);
        assertThat(this.<String>read(result, "$.node.status")).isEqualTo("Available");
        assertThat(node(getPath(anaToken, ana.getId()), "networking-basics"))
                .containsEntry("linkedCertificateId", certificateId);
        assertThat(status(requestBlueprint(anaToken, networkingId))).isEqualTo(201);
    }

    @Test
    void link_toALockedNode_linksItButTheAssessmentWaitsForThePrerequisites() throws Exception {
        int restId = nodeId(declare(anaToken, REST_AND_JWT), "rest-api-design");
        int certificateId = uploadEvidence("Curso de backend",
                "Temas del programa: servicios web, endpoints y versionado de una API");

        MvcResult result = linkCertificate(anaToken, restId, certificateId);

        assertThat(status(result)).isEqualTo(200);
        assertThat(this.<Double>read(result, "$.affinity")).isEqualTo(1.0);
        assertThat(this.<Boolean>read(result, "$.assessmentEnabled")).isFalse();
    }

    @Test
    @DisplayName("US15 escenario 2: a certificate that does not cover the skill is not linked and the nodes it covers are suggested")
    void link_aCertificateThatDoesNotCoverTheSkill_returns422WithTheSuggestedNodes() throws Exception {
        MvcResult path = declare(anaToken, REST_AND_JWT);
        int certificateId = uploadEvidence("Fundamentos de HTTP", "");

        MvcResult result = linkCertificate(anaToken, nodeId(path, "networking-basics"), certificateId);

        assertThat(status(result)).isEqualTo(422);
        assertThat(this.<String>read(result, "$.title")).isEqualTo("CertificateSkillMismatch");
        assertThat(this.<Double>read(result, "$.affinity")).isEqualTo(0.0);
        assertThat(this.<Double>read(result, "$.threshold")).isEqualTo(0.7);
        assertThat(this.<List<String>>read(result, "$.suggestedNodes[*].skillTag")).containsExactly("http-basics");
        assertThat(this.<List<Integer>>read(result, "$.suggestedNodes[*].nodeId"))
                .containsExactly(nodeId(path, "http-basics"));
        assertThat(queryString("SELECT count(*) FROM path_nodes WHERE linked_certificate_id IS NOT NULL"))
                .isEqualTo("0");
    }

    @Test
    void link_aSuspiciousCertificate_returns409() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");
        int certificateId = uploadSuspicious("Redes de computadoras");

        MvcResult result = linkCertificate(anaToken, networkingId, certificateId);

        assertThat(status(result)).isEqualTo(409);
        assertThat(this.<String>read(result, "$.title")).isEqualTo("CertificateNotVerified");
    }

    @Test
    void link_anotherStudentsCertificateOrNode_returns403() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");
        MvcResult bobUpload = upload(bobToken, newFile(), "Redes de computadoras", "");
        int bobCertificate = read(bobUpload, "$.id");

        MvcResult foreignCertificate = linkCertificate(anaToken, networkingId, bobCertificate);
        MvcResult foreignNode = linkCertificate(bobToken, networkingId, bobCertificate);

        assertThat(status(foreignCertificate)).isEqualTo(403);
        assertThat(this.<String>read(foreignCertificate, "$.title")).isEqualTo("NotCertificateOwner");
        assertThat(status(foreignNode)).isEqualTo(403);
        assertThat(this.<String>read(foreignNode, "$.title")).isEqualTo("NotPathOwner");
    }

    @Test
    void link_anUnknownOrMissingCertificate_returns404AndWithoutToken401() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");

        assertThat(status(linkCertificate(anaToken, networkingId, 9999))).isEqualTo(404);
        MvcResult missing = linkCertificate(anaToken, networkingId, null);
        assertThat(status(missing)).isEqualTo(404);
        assertThat(this.<String>read(missing, "$.title")).isEqualTo("CertificateNotFound");
        assertThat(status(linkCertificate(anaToken, 9999, 1))).isEqualTo(404);
        assertThat(status(linkCertificate(null, networkingId, 1))).isEqualTo(401);
    }

    @Test
    void link_errorMessagesFollowTheAcceptLanguageHeader() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");
        int certificateId = uploadEvidence("Fundamentos de HTTP", "");

        MvcResult result = mockMvc.perform(post("/api/v1/path-nodes/" + networkingId + "/certificate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("certificateId", certificateId)))
                        .header("Accept-Language", "es-419")
                        .header("Authorization", "Bearer " + anaToken))
                .andReturn();

        assertThat(this.<String>read(result, "$.detail")).startsWith("Este certificado no corresponde");
    }

    // ---------- US17 escenario 3: new attempts ----------

    @Test
    @DisplayName("US17 escenario 3: a new attempt excludes the previous questions and gets different ones")
    void newAttempt_sendsThePreviousQuestionsAndReturnsDifferentOnes() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");
        MvcResult first = requestBlueprint(anaToken, networkingId);
        generator.repeatPreviousQuestions(1, 3);

        MvcResult second = requestBlueprint(anaToken, networkingId);

        assertThat(status(second)).isEqualTo(201);
        List<String> firstQuestions = read(first, "$.questions[*].question");
        List<String> secondQuestions = read(second, "$.questions[*].question");
        assertThat(secondQuestions).hasSize(5).doesNotHaveDuplicates().doesNotContainAnyElementsOf(firstQuestions);
        assertThat(generator.exclusions().get(1)).containsExactlyInAnyOrderElementsOf(firstQuestions);
    }

    @Test
    void newAttempt_whenTheAiKeepsRepeating_returns503AndKeepsThePreviousAssessment() throws Exception {
        int networkingId = nodeId(declare(anaToken, REST_AND_JWT), "networking-basics");
        int firstId = read(requestBlueprint(anaToken, networkingId), "$.id");
        generator.repeatPreviousQuestions(Integer.MAX_VALUE, 5);

        MvcResult second = requestBlueprint(anaToken, networkingId);

        assertThat(status(second)).isEqualTo(503);
        assertThat(this.<String>read(second, "$.title")).isEqualTo("QuestionGenerationFailed");
        assertThat(node(getPath(anaToken, ana.getId()), "networking-basics"))
                .containsEntry("assessmentBlueprintId", firstId);
        assertThat(queryString("SELECT count(*) FROM assessment_blueprints")).isEqualTo("1");
    }
}
