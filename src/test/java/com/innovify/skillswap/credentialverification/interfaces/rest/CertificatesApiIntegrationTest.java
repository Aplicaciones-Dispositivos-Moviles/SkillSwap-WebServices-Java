package com.innovify.skillswap.credentialverification.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The certificate API end to end: real security filter, JWT, services and PostgreSQL. It also holds the
 * scenarios of the three C# feature files (CertificateUpload, CertificateStatus and CertificateDuplicates).
 */
class CertificatesApiIntegrationTest extends PostgresIntegrationTest {

    private static final String URL = "/api/v1/certificates";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    private MockMvc mockMvc;
    private User ana;
    private User bob;
    private String anaToken;
    private String bobToken;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        anaToken = tokenGenerator.generateToken(ana);
        bobToken = tokenGenerator.generateToken(bob);
    }

    // ---------- Helpers ----------

    private static byte[] withHeader(int[] header, String seed) {
        byte[] body = seed.getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[header.length + body.length];
        for (int i = 0; i < header.length; i++) {
            file[i] = (byte) header[i];
        }
        System.arraycopy(body, 0, file, header.length, body.length);
        return file;
    }

    private static byte[] jpeg(String seed) {
        return withHeader(new int[]{0xFF, 0xD8, 0xFF, 0xE0}, seed);
    }

    private static byte[] png(String seed) {
        return withHeader(new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, seed);
    }

    private static byte[] pdf(String seed) {
        return withHeader(new int[]{0x25, 0x50, 0x44, 0x46, 0x2D}, seed);
    }

    private MvcResult upload(String token, byte[] file, String contentType, Map<String, String> fields)
            throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(URL);
        if (file != null) {
            request.file(new MockMultipartFile("file", "certificate", contentType, file));
        }
        fields.forEach(request::param);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private int uploadOk(String token, String seed, String number, String code) throws Exception {
        Map<String, String> fields = new HashMap<>();
        if (number != null) {
            fields.put("certificateNumber", number);
        }
        if (code != null) {
            fields.put("verificationCode", code);
        }
        MvcResult result = upload(token, jpeg(seed), "image/jpeg", fields);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String field(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }

    // ---------- Security ----------

    @Test
    void upload_withoutToken_returns401() throws Exception {
        assertThat(upload(null, jpeg("x"), "image/jpeg", Map.of()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void getById_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL + "/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void upload_isPersistedWithTheTextTheCSharpApiWrites() throws Exception {
        uploadOk(anaToken, "file-a", "cert-001", null);

        assertThat(queryString("SELECT status FROM certificates")).isEqualTo("Unverified");
        assertThat(queryString("SELECT verification_method FROM certificates")).isEqualTo("OcrOnly");
        assertThat(queryString("SELECT certificate_number FROM certificates")).isEqualTo("CERT-001");
    }

    // ---------- CertificateUpload.feature ----------

    @DisplayName("Upload a file in an accepted format")
    @ParameterizedTest
    @ValueSource(strings = {"JPEG", "PNG", "PDF"})
    void uploadInAnAcceptedFormat_returns201Unverified(String format) throws Exception {
        MvcResult result = switch (format) {
            case "JPEG" -> upload(anaToken, jpeg("format-jpeg"), "image/jpeg", Map.of());
            case "PNG" -> upload(anaToken, png("format-png"), "image/png", Map.of());
            default -> upload(anaToken, pdf("format-pdf"), "application/pdf", Map.of());
        };

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(result, "$.status")).isEqualTo("Unverified");
    }

    @Test
    @DisplayName("Reject a file in a format that is not allowed")
    void uploadOfATextFile_returns415WithTheMessage() throws Exception {
        MvcResult result = upload(anaToken, "plain text".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(field(result, "$.title")).isEqualTo("InvalidFileType");
        assertThat(field(result, "$.detail")).isEqualTo("Only JPG, PNG and PDF files are accepted.");
    }

    @Test
    @DisplayName("Reject a file larger than 10 MB")
    void uploadOfAFileOverTenMegabytes_returns413() throws Exception {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(jpeg(""), 0, big, 0, 4);

        MvcResult result = upload(anaToken, big, "image/jpeg", Map.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(413);
        assertThat(field(result, "$.title")).isEqualTo("FileTooLarge");
    }

    @Test
    @DisplayName("Reject a request without a file")
    void uploadWithoutAFile_returns400() throws Exception {
        MvcResult result = upload(anaToken, null, null, Map.of("holderName", "Ana Perez"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(field(result, "$.title")).isEqualTo("FileRequired");
    }

    // ---------- CertificateDuplicates.feature ----------

    @Test
    @DisplayName("Register an original certificate")
    void originalCertificate_isUnverifiedWithLowRisk() throws Exception {
        MvcResult result = upload(anaToken, jpeg("original"), "image/jpeg", Map.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(result, "$.status")).isEqualTo("Unverified");
        assertThat(field(result, "$.riskLevel")).isEqualTo("LowRisk");
    }

    @Test
    @DisplayName("Reject a file the same student already registered")
    void sameFileFromTheSameStudent_returns409WithTheExistingId() throws Exception {
        int existing = uploadOk(anaToken, "original", null, null);

        MvcResult result = upload(anaToken, jpeg("original"), "image/jpeg", Map.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(field(result, "$.title")).isEqualTo("DuplicateFile");
        assertThat(field(result, "$.existingCertificateId")).isEqualTo(String.valueOf(existing));
    }

    @Test
    @DisplayName("A certificate number registered by another student requires review")
    void numberOfAnotherStudent_requiresReview() throws Exception {
        uploadOk(anaToken, "ana-file", "CERT-001", "CODE-A");

        MvcResult result = upload(bobToken, jpeg("bob-file"), "image/jpeg",
                Map.of("certificateNumber", "CERT-001", "verificationCode", "CODE-B"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(result, "$.status")).isEqualTo("Unverified");
        assertThat(field(result, "$.riskLevel")).isEqualTo("Review");
    }

    @Test
    @DisplayName("A number and a code registered by another student flag the certificate as suspicious")
    void numberAndCodeOfAnotherStudent_areSuspicious() throws Exception {
        uploadOk(anaToken, "ana-file", "CERT-001", "CODE-A");

        MvcResult result = upload(bobToken, jpeg("bob-file"), "image/jpeg",
                Map.of("certificateNumber", "cert-001", "verificationCode", "code-a"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(result, "$.status")).isEqualTo("Suspicious");
        assertThat(field(result, "$.riskLevel")).isEqualTo("HighRisk");
    }

    @Test
    @DisplayName("A file already registered by another student flags the certificate as suspicious")
    void fileOfAnotherStudent_isSuspicious() throws Exception {
        uploadOk(anaToken, "shared-file", null, null);

        MvcResult result = upload(bobToken, jpeg("shared-file"), "image/jpeg", Map.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(result, "$.status")).isEqualTo("Suspicious");
        assertThat(field(result, "$.riskLevel")).isEqualTo("HighRisk");
    }

    // ---------- CertificateStatus.feature ----------

    @Test
    @DisplayName("A student lists their certificates")
    void studentListsTheirCertificates() throws Exception {
        uploadOk(anaToken, "file-1", null, null);
        uploadOk(anaToken, "file-2", null, null);
        uploadOk(bobToken, "file-3", null, null);

        mockMvc.perform(get(URL).param("ownerId", String.valueOf(ana.getId()))
                        .header("Authorization", "Bearer " + anaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("A student opens one of their certificates")
    void studentOpensTheirCertificate() throws Exception {
        int id = uploadOk(anaToken, "file-1", null, null);

        mockMvc.perform(get(URL + "/" + id).header("Authorization", "Bearer " + anaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Unverified"));
    }

    @Test
    @DisplayName("A student cannot list another student's certificates")
    void studentCannotListAnotherStudentsCertificates() throws Exception {
        uploadOk(anaToken, "file-1", null, null);

        mockMvc.perform(get(URL).param("ownerId", String.valueOf(ana.getId()))
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("NotCertificateOwner"));
    }

    @Test
    @DisplayName("A student cannot open another student's certificate")
    void studentCannotOpenAnotherStudentsCertificate() throws Exception {
        int id = uploadOk(anaToken, "file-1", null, null);

        mockMvc.perform(get(URL + "/" + id).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("NotCertificateOwner"));
    }
}
