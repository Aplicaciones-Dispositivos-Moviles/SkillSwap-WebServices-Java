package com.innovify.skillswap.shared.interfaces.rest;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innovify.skillswap.support.PostgresIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The API documentation is public, lists the endpoints of every context and offers the JWT scheme. */
class ApiDocumentationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void root_withoutToken_redirectsToTheDocumentation() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void apiDocs_withoutToken_listTheEndpointsOfTheContexts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/api/v1/wallets/{userId}")))
                .andExpect(content().string(Matchers.containsString("/api/v1/verification-cases")))
                .andExpect(content().string(Matchers.containsString("/api/v1/verifier-reliabilities/{verifierUserId}")))
                .andExpect(content().string(Matchers.containsString("bearerAuth")));
    }

    @Test
    void apiDocs_groupTheEndpointsUnderReadableNames() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"name\":\"Verification Cases\"")))
                .andExpect(content().string(Matchers.containsString("\"name\":\"Wallets\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("verification-cases-controller"))));
    }

    @Test
    void apiDocs_documentTheTypesOfAVerificationCase() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"VerificationCaseResource\"")))
                .andExpect(content().string(Matchers.containsString("\"caseType\"")))
                .andExpect(content().string(Matchers.containsString("\"MiniProject\"")));
    }

    @Test
    void apiDocs_listTheSubscriptionEndpointsAndTheWebhook() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/api/v1/subscriptions/{id}/cancel")))
                .andExpect(content().string(Matchers.containsString("/api/v1/learning-paths/{pathId}/resume")))
                .andExpect(content().string(Matchers.containsString("/api/v1/subscriptions/webhooks/revenuecat")))
                .andExpect(content().string(Matchers.containsString("\"StudentPlanResource\"")))
                .andExpect(content().string(Matchers.containsString("\"name\":\"Subscriptions\"")))
                .andExpect(content().string(Matchers.containsString("\"name\":\"RevenueCat Webhook\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("revenue-cat-webhook-controller"))));
    }

    @Test
    void apiEndpoints_stillNeedAToken() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/1")).andExpect(status().isUnauthorized());
    }
}
