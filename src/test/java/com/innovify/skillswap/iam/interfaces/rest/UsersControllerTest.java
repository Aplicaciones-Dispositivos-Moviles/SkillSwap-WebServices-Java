package com.innovify.skillswap.iam.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class UsersControllerTest extends IamRestTest {

    private User ana;
    private User bob;
    private User coordinator;

    @BeforeEach
    void saveUsers() {
        ana = saveUser("ana", "ana@upc.edu.pe", UserRole.STUDENT);
        bob = saveUser("bob", "bob@upc.edu.pe", UserRole.STUDENT);
        coordinator = saveUser("root", "root@upc.edu.pe", UserRole.COORDINATOR);
    }

    private ResultActions updateBio(int id, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/" + id + "/bio")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---------- GET /me ----------

    @Test
    void getCurrentUser_returnsTheFullProfile() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.getId()))
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.email").value("ana@upc.edu.pe"))
                .andExpect(jsonPath("$.role").value("Student"))
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.bio").value(""));
    }

    // ---------- GET /{id} ----------

    @Test
    void getUserById_ofYourself_includesTheEmail() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(get("/api/v1/users/" + ana.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@upc.edu.pe"));
    }

    @Test
    void getUserById_ofAnotherStudent_omitsTheEmail() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(get("/api/v1/users/" + bob.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.role").value("Student"))
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void getUserById_asCoordinator_includesTheEmailOfAnyUser() throws Exception {
        authenticateAs(coordinator);

        mockMvc.perform(get("/api/v1/users/" + bob.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@upc.edu.pe"));
    }

    @Test
    void getUserById_withUnknownId_returns404() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("UserNotFound"))
                .andExpect(jsonPath("$.detail").value("The specified user was not found."));
    }

    @Test
    void getUserById_withANonNumericId_returns404() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(get("/api/v1/users/abc")).andExpect(status().isNotFound());
    }

    // ---------- PATCH /{id}/bio ----------

    @Test
    void updateBio_onYourOwnProfile_returns200AndPersistsIt() throws Exception {
        authenticateAs(ana);

        updateBio(ana.getId(), json("bio", "Backend developer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Backend developer"));

        assertThat(repository.findById(ana.getId()).orElseThrow().getBio()).isEqualTo("Backend developer");
    }

    @Test
    void updateBio_onAnotherProfile_returns403AndLeavesItUnchanged() throws Exception {
        authenticateAs(ana);

        updateBio(bob.getId(), json("bio", "hacked"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("NotProfileOwner"));

        assertThat(repository.findById(bob.getId()).orElseThrow().getBio()).isEmpty();
    }

    @Test
    void updateBio_exceedingTheMaximumLength_returns400() throws Exception {
        authenticateAs(ana);

        updateBio(ana.getId(), json("bio", "a".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("BioTooLong"));
    }

    @Test
    void updateBio_withoutTheBioField_returns400() throws Exception {
        authenticateAs(ana);

        updateBio(ana.getId(), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void updateBio_ofAnUnknownUser_returns404() throws Exception {
        authenticateAs(ana);

        updateBio(999, json("bio", "hi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("UserNotFound"));
    }
}
