package com.innovify.skillswap.iam.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @BeforeEach
    void saveUsers() {
        ana = saveUser("ana", "ana@upc.edu.pe", UserRole.STUDENT);
        bob = saveUser("bob", "bob@upc.edu.pe", UserRole.STUDENT);
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
                .andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.interests").isEmpty())
                .andExpect(jsonPath("$.skillVector").isEmpty());
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

    // ---------- Device token ----------

    @Test
    void registerDeviceToken_returns204AndStoresTheToken() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(put("/api/v1/users/me/device-token").contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", "fcm-token-of-ana")))
                .andExpect(status().isNoContent());

        assertThat(ana.getDeviceToken().value()).isEqualTo("fcm-token-of-ana");
    }

    @Test
    void registerDeviceToken_withAnInvalidToken_returns400() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(put("/api/v1/users/me/device-token").contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("InvalidDeviceToken"));
        mockMvc.perform(put("/api/v1/users/me/device-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeDeviceToken_returns204AndForgetsTheToken() throws Exception {
        ana.registerDeviceToken("fcm-token-of-ana");
        authenticateAs(ana);

        mockMvc.perform(delete("/api/v1/users/me/device-token")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/users/me/device-token")).andExpect(status().isNoContent());

        assertThat(ana.getDeviceToken()).isNull();
    }

    // ---------- PUT /{id}/interests (US04) ----------

    private ResultActions updateInterests(int id, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/users/" + id + "/interests")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void updateInterests_returnsTheProfileWithTheTopicsAndTheSkillVector() throws Exception {
        authenticateAs(ana);

        updateInterests(ana.getId(), "{\"topics\":[\"Backend con Java\",\"React\"],"
                + "\"description\":\"Me interesa el testing\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests[0]").value("Backend con Java"))
                .andExpect(jsonPath("$.interests[1]").value("React"))
                .andExpect(jsonPath("$.bio").value("Me interesa el testing"))
                .andExpect(jsonPath("$.skillVector[0]").value("java-language"))
                .andExpect(jsonPath("$.skillVector[1]").value("react"))
                .andExpect(jsonPath("$.skillVector[2]").value("software-testing"));
    }

    @Test
    void updateInterests_twice_replacesTheTopics() throws Exception {
        authenticateAs(ana);
        updateInterests(ana.getId(), "{\"topics\":[\"Java\"]}").andExpect(status().isOk());

        updateInterests(ana.getId(), "{\"topics\":[\"SQL\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(1))
                .andExpect(jsonPath("$.interests[0]").value("SQL"))
                .andExpect(jsonPath("$.skillVector.length()").value(1))
                .andExpect(jsonPath("$.skillVector[0]").value("sql-databases"));
    }

    @Test
    void updateInterests_areVisibleInThePublicProfile() throws Exception {
        authenticateAs(ana);
        updateInterests(ana.getId(), "{\"topics\":[\"Java\"]}").andExpect(status().isOk());
        authenticateAs(bob);

        mockMvc.perform(get("/api/v1/users/" + ana.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests[0]").value("Java"))
                .andExpect(jsonPath("$.skillVector[0]").value("java-language"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void updateInterests_ofAnotherUser_returns403() throws Exception {
        authenticateAs(bob);

        updateInterests(ana.getId(), "{\"topics\":[\"Java\"]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("NotProfileOwner"));
    }

    @Test
    void updateInterests_withInvalidTopics_returns400WithTheErrorCode() throws Exception {
        authenticateAs(ana);

        updateInterests(ana.getId(), "{\"topics\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("InterestTopicsRequired"));
        updateInterests(ana.getId(), "{\"topics\":[\"  \"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("InvalidInterestTopic"));
        updateInterests(ana.getId(), "{\"topics\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\","
                        + "\"10\",\"11\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("TooManyInterestTopics"));
        updateInterests(ana.getId(), "{\"description\":\"sin temas\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateInterests_errorsAreLocalized() throws Exception {
        authenticateAs(ana);

        mockMvc.perform(put("/api/v1/users/" + ana.getId() + "/interests").header("Accept-Language", "es-PE")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"topics\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Registra al menos un tema de interés."));
    }
}
