package com.innovify.skillswap.shared.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

    @Test
    void checkHealth_returnsHealthyWithoutAToken() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Healthy"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
