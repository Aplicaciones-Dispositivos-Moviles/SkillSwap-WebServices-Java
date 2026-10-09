package com.innovify.skillswap.credentialverification.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innovify.skillswap.credentialverification.application.fakes.FakeCertificateRepository;
import com.innovify.skillswap.credentialverification.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.credentialverification.application.fakes.FakeFileStorageService;
import com.innovify.skillswap.credentialverification.application.internal.commandservices.CertificateCommandServiceImpl;
import com.innovify.skillswap.credentialverification.application.internal.queryservices.CertificateQueryServiceImpl;
import com.innovify.skillswap.credentialverification.application.fakes.FakeIamContextFacade;
import com.innovify.skillswap.credentialverification.domain.services.DefaultCertificateRiskScorer;
import com.innovify.skillswap.credentialverification.domain.services.HolderNameMatcher;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.shared.infrastructure.i18n.LatinAmericanSpanishLocaleResolver;
import com.innovify.skillswap.shared.interfaces.rest.RestExceptionHandler;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Runs the controller in a standalone MockMvc over the real application services and in-memory fakes, so no
 * database is needed. The token filter and the role rule of the upload are covered by
 * CertificatesApiIntegrationTest, which uses a real PostgreSQL.
 */
class CertificatesControllerTest {

    private static final String URL = "/api/v1/certificates";

    private final FakeUserRepository users = new FakeUserRepository();
    private MockMvc mockMvc;
    private User ana;
    private User bob;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        var certificates = new FakeCertificateRepository();
        var storage = new FakeFileStorageService();
        var commands = new CertificateCommandServiceImpl(certificates, new DefaultCertificateRiskScorer(),
                new HolderNameMatcher(), storage, new FakeIamContextFacade(), new FakeDomainEventPublisher(), messages);
        var queries = new CertificateQueryServiceImpl(certificates, storage);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CertificatesController(commands, queries, messages))
                .setControllerAdvice(new RestExceptionHandler(), new CertificateUploadExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setLocaleResolver(new LatinAmericanSpanishLocaleResolver())
                .build();

        ana = users.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        bob = users.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT));
        authenticateAs(ana);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        LocaleContextHolder.resetLocaleContext();
    }

    private static void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    // ---------- Helpers ----------

    /** A valid JPEG whose content (and hash) depends on the seed. */
    private static byte[] jpeg(String seed) {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] body = seed.getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[header.length + body.length];
        System.arraycopy(header, 0, file, 0, header.length);
        System.arraycopy(body, 0, file, header.length, body.length);
        return file;
    }

    private static Map<String, String> ocr(String number, String code) {
        return Map.of("holderName", "Ana Perez", "institutionName", "Coursera",
                "courseName", "Backend with Spring", "issueDate", "2025-03-10", "durationHours", "40",
                "certificateNumber", number, "verificationCode", code,
                "verificationUrl", "https://example.com/verify/1");
    }

    private static Map<String, String> ocr() {
        return ocr("cert-001", "code-xyz");
    }

    private MockMultipartHttpServletRequestBuilder uploadRequest(byte[] file, String contentType,
                                                                 Map<String, String> fields) {
        MockMultipartHttpServletRequestBuilder request = multipart(URL);
        if (file != null) {
            request.file(new MockMultipartFile("file", "certificate", contentType, file));
        }
        fields.forEach(request::param);
        return request;
    }

    private MvcResult upload(byte[] file, String contentType, Map<String, String> fields) throws Exception {
        return mockMvc.perform(uploadRequest(file, contentType, fields)).andReturn();
    }

    private int uploadOk(User user, String seed, Map<String, String> fields) throws Exception {
        authenticateAs(user);
        MvcResult result = upload(jpeg(seed), "image/jpeg", fields);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // ---------- Upload ----------

    @Test
    void upload_withValidFileAndOcrData_returns201WithTheCertificate() throws Exception {
        mockMvc.perform(uploadRequest(jpeg("default"), "image/jpeg", ocr()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(URL + "/")))
                .andExpect(jsonPath("$.ownerId").value(ana.getId()))
                .andExpect(jsonPath("$.holderName").value("Ana Perez"))
                .andExpect(jsonPath("$.institutionName").value("Coursera"))
                .andExpect(jsonPath("$.issueDate").value("2025-03-10"))
                .andExpect(jsonPath("$.durationHours").value(40))
                .andExpect(jsonPath("$.certificateNumber").value("CERT-001"))
                .andExpect(jsonPath("$.verificationCode").value("CODE-XYZ"))
                .andExpect(jsonPath("$.status").value("Unverified"))
                .andExpect(jsonPath("$.verificationMethod").value("OcrOnly"))
                .andExpect(jsonPath("$.riskLevel").value("LowRisk"))
                .andExpect(jsonPath("$.verifiedAt").doesNotExist())
                .andExpect(jsonPath("$.fileUrl").value(startsWith(
                        "https://files.test/stored/certificates/" + ana.getId() + "/")));
    }

    @Test
    void upload_locationHeader_pointsToTheCreatedCertificate() throws Exception {
        MvcResult result = upload(jpeg("default"), "image/jpeg", ocr());

        int id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo(URL + "/" + id);
    }

    @Test
    void upload_withoutAnyOcrData_returns201WithEmptyFields() throws Exception {
        mockMvc.perform(uploadRequest(jpeg("default"), "image/jpeg", Map.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holderName").doesNotExist())
                .andExpect(jsonPath("$.issueDate").doesNotExist())
                .andExpect(jsonPath("$.riskLevel").value("LowRisk"));
    }

    @Test
    void upload_ignoresTheOwnerIdSentInTheForm() throws Exception {
        mockMvc.perform(uploadRequest(jpeg("default"), "image/jpeg", Map.of("ownerId", "999")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(ana.getId()));
    }

    @Test
    void upload_withoutFile_returns400FileRequired() throws Exception {
        mockMvc.perform(uploadRequest(null, null, Map.of("holderName", "Ana Perez")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("FileRequired"));
    }

    @Test
    void upload_ofATextFile_returns415() throws Exception {
        mockMvc.perform(uploadRequest("plain text".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("InvalidFileType"));
    }

    @Test
    void upload_whenTheDeclaredTypeDoesNotMatchTheContent_returns415() throws Exception {
        mockMvc.perform(uploadRequest(jpeg("x"), "image/png", Map.of()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void upload_ofAFileOverTenMegabytes_returns413() throws Exception {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(jpeg(""), 0, big, 0, 4);

        mockMvc.perform(uploadRequest(big, "image/jpeg", Map.of()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("FileTooLarge"));
    }

    @Test
    void upload_withAFieldOverItsMaximumLength_returns400() throws Exception {
        mockMvc.perform(uploadRequest(jpeg("default"), "image/jpeg", Map.of("holderName", "a".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("FieldTooLong"));
    }

    @Test
    void upload_withAMalformedDate_returns400() throws Exception {
        mockMvc.perform(uploadRequest(jpeg("default"), "image/jpeg", Map.of("issueDate", "10/03/2025")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_ofTheSameFileTwice_returns409ReferencingTheExistingCertificate() throws Exception {
        int firstId = uploadOk(ana, "same-file", Map.of());

        mockMvc.perform(uploadRequest(jpeg("same-file"), "image/jpeg", Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("DuplicateFile"))
                .andExpect(jsonPath("$.existingCertificateId").value(firstId));
    }

    @Test
    void upload_ofAFileAnotherStudentAlreadyRegistered_isSuspicious() throws Exception {
        uploadOk(ana, "shared-file", Map.of());
        authenticateAs(bob);

        mockMvc.perform(uploadRequest(jpeg("shared-file"), "image/jpeg", Map.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Suspicious"))
                .andExpect(jsonPath("$.riskLevel").value("HighRisk"));
    }

    @Test
    void upload_withTheNumberAndCodeOfAnotherStudent_isSuspicious() throws Exception {
        uploadOk(ana, "file-a", ocr("cert-001", "code-a"));
        authenticateAs(bob);

        mockMvc.perform(uploadRequest(jpeg("file-b"), "image/jpeg", ocr("CERT-001", "CODE-A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Suspicious"))
                .andExpect(jsonPath("$.riskLevel").value("HighRisk"));
    }

    @Test
    void upload_withOnlyTheNumberOfAnotherStudent_needsReviewButIsNotSuspicious() throws Exception {
        uploadOk(ana, "file-a", ocr("cert-001", "code-a"));
        authenticateAs(bob);

        mockMvc.perform(uploadRequest(jpeg("file-b"), "image/jpeg", ocr("cert-001", "code-b")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Unverified"))
                .andExpect(jsonPath("$.riskLevel").value("Review"));
    }

    @Test
    void upload_responses_neverExposeInternalData() throws Exception {
        Map<String, String> fields = new HashMap<>(ocr());
        fields.put("ocrText", "secret text");
        fields.put("qrPayload", "qr");

        MvcResult result = upload(jpeg("default"), "image/jpeg", fields);

        String body = result.getResponse().getContentAsString();
        for (String internal : List.of("fileHash", "storageReference", "ocrText", "qrPayload", "riskScore")) {
            assertThat(body).as(internal).doesNotContain("\"" + internal + "\"");
        }
    }

    @Test
    void upload_errorMessages_followTheAcceptLanguageHeader() throws Exception {
        mockMvc.perform(uploadRequest("plain text".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of())
                        .header("Accept-Language", "es-PE"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Solo se aceptan archivos JPG, PNG y PDF."));
    }

    @Test
    void upload_errorMessages_areInEnglishWhenRequested() throws Exception {
        mockMvc.perform(uploadRequest("plain text".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of())
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Only JPG, PNG and PDF files are accepted."));
    }

    @Test
    void uploadExceptionHandler_answersTooBigRequestsWith413() throws Exception {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        LocaleContextHolder.setLocale(Locale.US);

        var response = new CertificateUploadExceptionHandler(messages)
                .handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(
                        25L * 1024 * 1024));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(((ProblemDetail) response.getBody()).getTitle()).isEqualTo("FileTooLarge");
    }

    // ---------- Read ----------

    @Test
    void getById_asOwner_returns200() throws Exception {
        int id = uploadOk(ana, "file-a", ocr());
        authenticateAs(ana);

        mockMvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("Unverified"));
    }

    @Test
    void getById_asAnotherStudent_returns403() throws Exception {
        int id = uploadOk(ana, "file-a", Map.of());
        authenticateAs(bob);

        mockMvc.perform(get(URL + "/" + id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("NotCertificateOwner"));
    }

    @Test
    void getById_withUnknownId_returns404() throws Exception {
        mockMvc.perform(get(URL + "/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("CertificateNotFound"));
    }

    @Test
    void list_withoutOwnerId_returnsTheCallersCertificatesNewestFirst() throws Exception {
        int first = uploadOk(ana, "file-1", Map.of());
        uploadOk(bob, "file-2", Map.of());
        int third = uploadOk(ana, "file-3", Map.of());
        authenticateAs(ana);

        MvcResult result = mockMvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))).andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).containsExactly(third, first);
    }

    @Test
    void list_ofAnotherStudent_returns403() throws Exception {
        authenticateAs(bob);

        mockMvc.perform(get(URL).param("ownerId", String.valueOf(ana.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("NotCertificateOwner"));
    }

}
