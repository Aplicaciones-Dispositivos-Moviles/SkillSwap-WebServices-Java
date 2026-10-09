package com.innovify.skillswap.credentialverification.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innovify.skillswap.credentialverification.application.commandservices.CertificateCommandService;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.fakes.FakePushNotificationSender;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushDeliveryResult;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US16 end to end: the device token registered through the API receives a push notification when a certificate
 * of the student is verified or rejected (with the reason), and nothing is sent when the student denied the
 * notification permission, while the new status is still returned by the certificate queries.
 */
class CertificateNotificationsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateCommandService certificateCommandService;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private FakePushNotificationSender pushSender;

    private MockMvc mockMvc;
    private User ana;
    private String anaToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        pushSender.clear();
        ana = userRepository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT).verify());
        anaToken = tokenGenerator.generateToken(ana);
    }

    /** A certificate of Ana escalated for review (high risk). */
    private Certificate suspiciousCertificate(String hash) {
        Certificate certificate = new Certificate(ana.getId(), hash.repeat(64), "certificates/" + hash)
                .applyExtractedData("Ana", "UPC", "Java Básico", LocalDate.of(2026, 1, 15), 40, null, null, null,
                        null, "texto");
        certificate.assessRisk(new RiskAssessment(80));
        return certificateRepository.save(certificate);
    }

    private void registerDeviceToken(String token) throws Exception {
        mockMvc.perform(put("/api/v1/users/me/device-token").header("Authorization", "Bearer " + anaToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void verifiedCertificate_sendsAPushToTheRegisteredDevice() throws Exception {
        registerDeviceToken("fcm-token-of-ana");
        Certificate certificate = suspiciousCertificate("a");

        certificateCommandService.handle(new ResolveCertificateDisputeCommand(certificate.getId(), true));

        assertThat(pushSender.sent()).singleElement().satisfies(push -> {
            assertThat(push.deviceToken()).isEqualTo("fcm-token-of-ana");
            assertThat(push.notification().title()).isEqualTo("Certificado verificado");
            assertThat(push.notification().body()).contains("Java Básico");
            assertThat(push.notification().data()).containsEntry("certificateId",
                    String.valueOf(certificate.getId())).containsEntry("status", "Verified");
        });
    }

    @Test
    void rejectedCertificate_sendsAPushWithTheReason() throws Exception {
        registerDeviceToken("fcm-token-of-ana");
        Certificate certificate = suspiciousCertificate("b");

        certificateCommandService.handle(new ResolveCertificateDisputeCommand(certificate.getId(), false,
                "El código de verificación no existe en el emisor"));

        assertThat(pushSender.sent()).singleElement().satisfies(push -> {
            assertThat(push.notification().title()).isEqualTo("Certificado rechazado");
            assertThat(push.notification().body())
                    .endsWith("Motivo: El código de verificación no existe en el emisor");
        });
    }

    @Test
    void withoutNotificationPermission_nothingIsSentButTheStatusCanBeQueried() throws Exception {
        registerDeviceToken("fcm-token-of-ana");
        // The student denied (revoked) the permission: the app removes the token.
        mockMvc.perform(delete("/api/v1/users/me/device-token").header("Authorization", "Bearer " + anaToken))
                .andExpect(status().isNoContent());
        Certificate certificate = suspiciousCertificate("c");

        certificateCommandService.handle(new ResolveCertificateDisputeCommand(certificate.getId(), false, "Ilegible"));

        assertThat(pushSender.sent()).isEmpty();
        assertThat(queryString("SELECT device_token FROM users WHERE username = 'ana'")).isNull();
        mockMvc.perform(get("/api/v1/certificates/" + certificate.getId())
                        .header("Authorization", "Bearer " + anaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Rejected"));
    }

    @Test
    void aTokenThatFcmNoLongerAccepts_isForgotten() throws Exception {
        registerDeviceToken("stale-token");
        pushSender.answer(PushDeliveryResult.INVALID_TOKEN);
        Certificate certificate = suspiciousCertificate("d");

        certificateCommandService.handle(new ResolveCertificateDisputeCommand(certificate.getId(), true));

        assertThat(pushSender.sent()).hasSize(1);
        assertThat(queryString("SELECT device_token FROM users WHERE username = 'ana'")).isNull();
    }

    @Test
    void registeringATokenUsedByAnotherAccount_movesItToTheNewAccount() throws Exception {
        User bob = userRepository.save(TestData.newUser("bob", "bob@upc.edu.pe", UserRole.STUDENT).verify()
                .registerDeviceToken("shared-device"));

        registerDeviceToken("shared-device");

        assertThat(queryString("SELECT device_token FROM users WHERE id = " + bob.getId())).isNull();
        assertThat(queryString("SELECT device_token FROM users WHERE id = " + ana.getId())).isEqualTo("shared-device");
    }

    @Test
    void deviceTokenEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/device-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/me/device-token")).andExpect(status().isUnauthorized());
    }
}
