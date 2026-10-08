package com.innovify.skillswap.iam.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    // ---------- Sign up ----------

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
                                + "\"password\":\"password123\",\"role\":\"Coordinator\"}"))
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
        signUp("ana", "ana@upc.edu.pe", PASSWORD);

        signIn("ANA", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.email").value("ana@upc.edu.pe"))
                .andExpect(jsonPath("$.role").value("Student"))
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty());
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
