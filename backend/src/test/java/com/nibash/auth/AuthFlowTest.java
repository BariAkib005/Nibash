package com.nibash.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nibash.building.BuildingRepository;
import com.nibash.building.BuildingSetting;
import com.nibash.building.BuildingSettingRepository;
import com.nibash.user.Roles;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Boot 4 moved MockMvc auto-configuration into the webmvc test module.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

/**
 * Week 1 definition of done: the signup → login → me → logout journey, plus the three documented
 * failure modes (spec §4.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BuildingRepository buildings;
    @Autowired BuildingSettingRepository settings;
    @Autowired ObjectMapper json;

    private static String unique(String prefix) {
        return prefix + System.nanoTime() + "@nibash.test";
    }

    @Test
    void signupCreatesUserBuildingSettingAndToken() throws Exception {
        String email = unique("founder");

        String body = mvc.perform(post("/api/auth/signup/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"name":"Ayesha Rahman","email":"%s","password":"Shokal!Bela42",
                                  "building_name":"Gulshan Lakeview Heights","modules":["Finance","Visitors"]}
                                 """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value(Roles.ADMIN))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.building.name").value("Gulshan Lakeview Heights"))
                // sensitive fields must never appear
                .andExpect(jsonPath("$.user.password_hash").doesNotExist())
                .andExpect(jsonPath("$.user.national_id").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode payload = json.readTree(body);
        long buildingId = payload.get("building").get("id").asLong();

        User created = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(created.getRole()).isEqualTo(Roles.ADMIN);
        assertThat(created.getPasswordHash()).startsWith("$2");   // BCrypt, not plaintext

        assertThat(buildings.findById(buildingId)).isPresent();

        BuildingSetting modules = settings
                .findByBuildingIdAndKeyName(buildingId, BuildingSetting.ENABLED_MODULES)
                .orElseThrow();
        assertThat(modules.getValueJson()).contains("Finance").contains("Visitors");
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        String email = unique("dup");
        String payload = """
                         {"name":"First Owner","email":"%s","password":"Shokal!Bela42"}
                         """.formatted(email);

        mvc.perform(post("/api/auth/signup/").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        // Commit so the duplicate check in the second request can see the first user.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        try {
            mvc.perform(post("/api/auth/signup/").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
        } finally {
            users.findByEmailIgnoreCase(email).ifPresent(users::delete);   // this test committed, so clean up
        }
    }

    @Test
    void signupEnforcesPasswordPolicy() throws Exception {
        mvc.perform(post("/api/auth/signup/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"name":"Weak Owner","email":"%s","password":"12345678"}
                                 """.formatted(unique("weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("entirely numeric")));
    }

    @Test
    void loginRejectsBadCredentialsWithTheContractMessage() throws Exception {
        mvc.perform(post("/api/auth/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"nobody@nibash.test","password":"WrongPassword1!"}
                                 """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/auth/me/"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void signupThenLoginThenMeThenLogout() throws Exception {
        String email = unique("journey");
        String signup = mvc.perform(post("/api/auth/signup/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"name":"Journey User","email":"%s","password":"Shokal!Bela42"}
                                 """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(signup).get("token").asString();
        assertThat(token).hasSize(40);

        mvc.perform(get("/api/auth/me/").header("Authorization", "Token " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.building").isNotEmpty());

        mvc.perform(post("/api/auth/logout/").header("Authorization", "Token " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detail").value("Logged out."));

        // The token is dead after logout.
        mvc.perform(get("/api/auth/me/").header("Authorization", "Token " + token))
                .andExpect(status().isUnauthorized());
    }
}
