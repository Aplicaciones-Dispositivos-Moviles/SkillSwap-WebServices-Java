package com.innovify.skillswap.iam.infrastructure.security;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The security rules over the whole application: no controller of the IAM context exists yet. */
class SecurityIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository repository;

    @Autowired
    private TokenGenerator tokenGenerator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void health_isOpenWithoutAToken() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void protectedPath_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPath_withAGarbageToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPath_withAValidToken_passesSecurity() throws Exception {
        User user = repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT));
        String token = tokenGenerator.generateToken(user);

        // No controller serves the path, so getting past security means a 404 instead of a 401.
        mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedPath_withATokenOfAUserThatNoLongerExists_isUnauthorized() throws Exception {
        String token = tokenGenerator.generateToken(
                repository.save(TestData.newUser("ana", "ana@upc.edu.pe", UserRole.STUDENT)));
        execute("TRUNCATE TABLE users");

        mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
