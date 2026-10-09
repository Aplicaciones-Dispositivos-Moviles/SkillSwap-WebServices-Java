package com.innovify.skillswap.iam.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.innovify.skillswap.iam.application.fakes.FakeEmailSender;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The IAM API end to end: real security filter, real services, BCrypt, JWT and PostgreSQL. */
class AuthenticationFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FakeEmailSender emailSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        emailSender.clear();
    }

    /** Signs up and opens the link of the verification email, as the student does before signing in. */
    private void signUp(String username, String email) throws Exception {
        mockMvc.perform(post("/api/v1/authentication/sign-up").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/authentication/verify-email").param("token", emailSender.lastTokenFor(email)))
                .andExpect(status().isOk());
    }

    private String signInAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/authentication/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void signUpThenSignIn_givesATokenThatOpensTheProfile() throws Exception {
        signUp("ana", "ana@upc.edu.pe");
        String token = signInAndGetToken("ANA");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.email").value("ana@upc.edu.pe"));
    }

    @Test
    void signUp_storesABCryptHashAndNeverThePlainPassword() throws Exception {
        signUp("ana", "ana@upc.edu.pe");

        String stored = queryString("SELECT password_hash FROM users WHERE username = 'ana'");
        assertThat(stored).startsWith("$2").isNotEqualTo("password123");
    }

    @Test
    void usersMe_withoutAToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersMe_withAGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateBio_persistsAndAnotherStudentSeesThePublicProfile() throws Exception {
        signUp("ana", "ana@upc.edu.pe");
        signUp("bob", "bob@upc.edu.pe");
        String anaToken = signInAndGetToken("ana");
        String bobToken = signInAndGetToken("bob");

        mockMvc.perform(patch("/api/v1/users/1/bio").header("Authorization", "Bearer " + anaToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"Backend developer\"}"))
                .andExpect(status().isOk());

        assertThat(queryString("SELECT bio FROM users WHERE username = 'ana'")).isEqualTo("Backend developer");
        mockMvc.perform(get("/api/v1/users/1").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Backend developer"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(patch("/api/v1/users/1/bio").header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"hacked\"}"))
                .andExpect(status().isForbidden());
    }

    /** US04 with the real skill catalog: the topics and the description become the skill vector, then replaced. */
    @Test
    void interestProfile_isSavedWithItsSkillVectorAndReplacedOnUpdate() throws Exception {
        signUp("ana", "ana@upc.edu.pe");
        String token = signInAndGetToken("ana");

        mockMvc.perform(put("/api/v1/users/1/interests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topics\":[\"Programación en Java\",\"Ajedrez\"],"
                                + "\"description\":\"Quiero aprender React\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(2))
                .andExpect(jsonPath("$.skillVector[0]").value("java-language"))
                .andExpect(jsonPath("$.skillVector[1]").value("react"));
        assertThat(queryString("SELECT interest_topics ->> 0 FROM users WHERE username = 'ana'"))
                .isEqualTo("Programación en Java");
        assertThat(queryString("SELECT bio FROM users WHERE username = 'ana'")).isEqualTo("Quiero aprender React");

        mockMvc.perform(put("/api/v1/users/1/interests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"topics\":[\"Python\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(1));

        assertThat(queryString("SELECT skill_vector::text FROM users WHERE username = 'ana'"))
                .contains("python-language").contains("react").doesNotContain("java-language");
    }
}
