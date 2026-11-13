package com.innovify.skillswap.iam.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

class AuthenticationControllerTest extends IamRestTest {

    private ResultActions signUp(String username, String email, String password) throws Exception {
        return mockMvc.perform(post(SIGN_UP_URL).contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "email", email, "password", password)));
    }

    private ResultActions signIn(String username, String password) throws Exception {
        return signIn(username, password, null);
    }

    private ResultActions signIn(String username, String password, String language) throws Exception {
        var request = post(SIGN_IN_URL).contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "password", password));
        if (language != null) {
            request = request.header("Accept-Language", language);
        }
        return mockMvc.perform(request);
    }

    private ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content(json("token", token)));
    }

    private ResultActions resend(String email, String language) throws Exception {
        var request = post(RESEND_URL).contentType(MediaType.APPLICATION_JSON).content(json("email", email));
        if (language != null) {
            request = request.header("Accept-Language", language);
        }
        return mockMvc.perform(request);
    }

    private static final String VERIFY_URL = "/api/v1/authentication/verify-email";
    private static final String RESEND_URL = "/api/v1/authentication/resend-verification";

    /** Signs up and verifies the email with the token of the verification email. */
    private void signUpVerified(String username, String email) throws Exception {
        signUp(username, email, PASSWORD).andExpect(status().isCreated());
        verifyEmail(lastVerificationTokenFor(email)).andExpect(status().isOk());
    }

    // ---------- Sign up ----------

    @Test
    void signUp_requestsAVerificationEmailForTheRegisteredAddress() throws Exception {
        signUp("ana", "Ana@UPC.edu.pe", PASSWORD).andExpect(status().isCreated());

        assertThat(verificationRequestsFor("ana@upc.edu.pe")).hasSize(1);
        assertThat(repository.users().get(0).getVerificationTokenHash())
                .isNotNull()
                .isNotEqualTo(lastVerificationTokenFor("ana@upc.edu.pe"));
    }

    @Test
    void signUp_withValidData_returns201AndTheProfileWithoutSecrets() throws Exception {
        String body = signUp("Ana", "Ana@UPC.edu.pe", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.email").value("ana@upc.edu.pe"))
                .andExpect(jsonPath("$.role").value("Student"))
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.bio").value(""))
                .andExpect(header().string("Location", "/api/v1/users/1"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body.toLowerCase()).doesNotContain("password");
    }

    @Test
    void signUp_ignoresTheRoleSentByTheClient() throws Exception {
        mockMvc.perform(post(SIGN_UP_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"email\":\"mallory@upc.edu.pe\","
                                + "\"password\":\"password123\",\"role\":\"Admin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("Student"));

        assertThat(repository.users().get(0).getRole()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void signUp_trimsTheUsernameAndTheEmail() throws Exception {
        signUp("  ana  ", "  ana@upc.edu.pe ", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test
    void signUp_withTakenUsername_returns409EvenWithDifferentCase() throws Exception {
        signUp("Ana", "ana@upc.edu.pe", PASSWORD);

        signUp("ANA", "other@upc.edu.pe", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("UsernameAlreadyTaken"));
    }

    @Test
    void signUp_withTakenEmail_returns409() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);

        signUp("other", "ANA@upc.edu.pe", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("EmailAlreadyTaken"));
    }

    @ParameterizedTest
    @CsvSource({
            "ana,ana@gmail.com,password123,InvalidInstitutionalEmail",
            "ana,ana@upc.edu,password123,InvalidInstitutionalEmail",
            "ana,ana@edu.pe,password123,InvalidInstitutionalEmail",
            "ab,ana@upc.edu.pe,password123,InvalidUsername",
            "'with space',ana@upc.edu.pe,password123,InvalidUsername",
            "ana,ana@upc.edu.pe,short,WeakPassword"})
    void signUp_withInvalidData_returns400WithTheErrorCode(String username, String email, String password,
                                                           String expectedError) throws Exception {
        signUp(username, email, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(expectedError));
    }

    @Test
    void signUp_withAMissingField_returns400() throws Exception {
        mockMvc.perform(post(SIGN_UP_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"email\":\"ana@upc.edu.pe\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Sign in ----------

    @Test
    void signIn_withCorrectCredentials_returns200AndAToken() throws Exception {
        signUpVerified("ana", "ana@upc.edu.pe");

        signIn("ANA", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.email").value("ana@upc.edu.pe"))
                .andExpect(jsonPath("$.role").value("Student"))
                .andExpect(jsonPath("$.isVerified").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void signIn_beforeVerifyingTheEmail_returns403EmailNotVerifiedWithoutAToken() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);

        signIn("ana", PASSWORD, "es-PE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("EmailNotVerified"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(
                        "Verifica tu correo institucional")))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void signIn_beforeVerifyingTheEmail_sendsTheVerificationEmailAgainOncePerCooldown() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);
        String firstToken = lastVerificationTokenFor("ana@upc.edu.pe");

        // Right after the sign-up email: nothing new is sent, the first link keeps working.
        signIn("ana", PASSWORD).andExpect(status().isForbidden());
        assertThat(verificationRequestsFor("ana@upc.edu.pe")).hasSize(1);

        clock.advance(RESEND_COOLDOWN);
        signIn("ana", PASSWORD).andExpect(status().isForbidden());
        signIn("ana", PASSWORD).andExpect(status().isForbidden());

        assertThat(verificationRequestsFor("ana@upc.edu.pe")).hasSize(2);
        String secondToken = lastVerificationTokenFor("ana@upc.edu.pe");
        assertThat(secondToken).isNotEqualTo(firstToken);
        verifyEmail(firstToken).andExpect(status().isBadRequest());
        verifyEmail(secondToken).andExpect(status().isOk());
        signIn("ana", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void signIn_ofAnUnverifiedAccountWithAWrongPassword_returns401AndSendsNothing() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);
        clock.advance(RESEND_COOLDOWN);

        signIn("ana", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("InvalidCredentials"));

        assertThat(verificationRequestsFor("ana@upc.edu.pe")).hasSize(1);
    }

    // ---------- Email verification ----------

    @Test
    void verifyEmail_withTheTokenOfTheEmail_returns200AndTheVerifiedProfile() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);

        verifyEmail(lastVerificationTokenFor("ana@upc.edu.pe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.isVerified").value(true));

        assertThat(repository.users().get(0).isVerified()).isTrue();
        assertThat(repository.users().get(0).getVerificationTokenHash()).isNull();
    }

    @Test
    void verifyEmail_twiceWithTheSameToken_returns400TheSecondTime() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);
        String token = lastVerificationTokenFor("ana@upc.edu.pe");
        verifyEmail(token).andExpect(status().isOk());

        verifyEmail(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("InvalidVerificationToken"));
    }

    @Test
    void verifyEmail_withAnUnknownToken_returns400() throws Exception {
        verifyEmail("not-a-real-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("InvalidVerificationToken"));
    }

    @Test
    void verifyEmail_afterTheTokenExpired_returns410AndKeepsTheAccountUnverified() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);
        clock.advance(TOKEN_TTL);

        verifyEmail(lastVerificationTokenFor("ana@upc.edu.pe"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("VerificationTokenExpired"));

        assertThat(repository.users().get(0).isVerified()).isFalse();
    }

    @Test
    void verifyEmail_withoutAToken_returns400() throws Exception {
        mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmailLink_openedInABrowser_verifiesAndAnswersAPage() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);

        mockMvc.perform(get(VERIFY_URL).param("token", lastVerificationTokenFor("ana@upc.edu.pe")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tu correo fue verificado")));

        assertThat(repository.users().get(0).isVerified()).isTrue();
    }

    @Test
    void verifyEmailLink_withAnInvalidOrMissingToken_answersAnErrorPage() throws Exception {
        mockMvc.perform(get(VERIFY_URL).param("token", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("no es válido")));
        mockMvc.perform(get(VERIFY_URL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmailLink_afterTheTokenExpired_answers410() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);
        clock.advance(TOKEN_TTL.plusMinutes(1));

        mockMvc.perform(get(VERIFY_URL).param("token", lastVerificationTokenFor("ana@upc.edu.pe")))
                .andExpect(status().isGone())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("venció")));
    }

    // ---------- Resend verification ----------

    @Test
    void resendVerification_forAnUnverifiedAccount_returns202AndSendsANewEmailAfterTheCooldown()
            throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);

        resend("ana@upc.edu.pe", null).andExpect(status().isAccepted());
        assertThat(verificationRequestsFor("ana@upc.edu.pe")).hasSize(1);

        clock.advance(Duration.ofMinutes(3));
        resend("ANA@upc.edu.pe", null).andExpect(status().isAccepted());
        assertThat(verificationRequestsFor("ana@upc.edu.pe")).hasSize(2);
    }

    @Test
    void resendVerification_answersTheSameForUnknownVerifiedAndUnverifiedAccounts() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);
        signUpVerified("bob", "bob@upc.edu.pe");
        clock.advance(RESEND_COOLDOWN);
        int requestsBefore = verificationRequestsFor("bob@upc.edu.pe").size();

        String unverified = resend("ana@upc.edu.pe", "es-PE").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String verified = resend("bob@upc.edu.pe", "es-PE").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String unknown = resend("nobody@upc.edu.pe", "es-PE").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String notInstitutional = resend("someone@gmail.com", "es-PE").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(unverified).isEqualTo(verified).isEqualTo(unknown).isEqualTo(notInstitutional)
                .contains("te enviaremos un nuevo correo de verificación");
        assertThat(verificationRequestsFor("bob@upc.edu.pe")).hasSize(requestsBefore);
        assertThat(verificationRequestsFor("nobody@upc.edu.pe")).isEmpty();
    }

    @Test
    void resendVerification_withoutAnEmail_returns400() throws Exception {
        mockMvc.perform(post(RESEND_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signIn_withWrongPasswordOrUnknownUser_returns401WithTheSameError() throws Exception {
        signUp("ana", "ana@upc.edu.pe", PASSWORD);

        String wrongPassword = signIn("ana", "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("InvalidCredentials"))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = signIn("nobody", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("InvalidCredentials"))
                .andReturn().getResponse().getContentAsString();

        // Same title and same detail, so the response does not reveal which usernames exist.
        assertThat(extractDetail(wrongPassword)).isEqualTo(extractDetail(unknownUser));
    }

    private static String extractDetail(String problemJson) {
        int start = problemJson.indexOf("\"detail\":\"") + "\"detail\":\"".length();
        return problemJson.substring(start, problemJson.indexOf('"', start));
    }

    // ---------- Localization ----------

    @ParameterizedTest
    @CsvSource(value = {
            "es-PE,Usuario o contraseña incorrectos.",
            "es-MX,Usuario o contraseña incorrectos.",
            "es,Usuario o contraseña incorrectos.",
            "en-US,Invalid username or password.",
            "fr-FR,Invalid username or password.",
            "null,Invalid username or password."}, nullValues = "null")
    void errorMessages_followTheAcceptLanguageHeader(String language, String expectedDetail) throws Exception {
        signIn("nobody", PASSWORD, language)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(expectedDetail));
    }
}
