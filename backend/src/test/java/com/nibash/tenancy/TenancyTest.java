package com.nibash.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nibash.seed.DemoSeeder;
import com.nibash.unit.UnitRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Week 2 definition of done (plan §Week 2): tenant isolation, role gating, directory privacy and
 * seeder idempotency — the guarantees that must never regress.
 *
 * <p>These run against the seeded {@code nibash_test} schema. The seeder is idempotent, so seeding
 * once for the class is safe and repeatable.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenancyTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DemoSeeder seeder;
    @Autowired UnitRepository units;

    private static boolean seeded = false;

    @BeforeAll
    static void resetFlag() {
        seeded = false;
    }

    private void ensureSeeded() {
        if (!seeded) {
            seeder.seed();
            seeded = true;
        }
    }

    private String tokenFor(String email) throws Exception {
        ensureSeeded();
        String body = mvc.perform(post("/api/auth/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"%s","password":"%s"}
                                 """.formatted(email, DemoSeeder.DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asString();
    }

    private long buildingIdFor(String email) throws Exception {
        ensureSeeded();
        String body = mvc.perform(get("/api/auth/me/").header("Authorization", "Token " + tokenFor(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("building").get("id").asLong();
    }

    // ---------------------------------------------------------------- tenancy

    @Test
    void allFiveDemoRolesCanLogIn() throws Exception {
        for (String account : new String[]{"admin1", "committee1", "resident1", "guard1", "staff1"}) {
            assertThat(tokenFor(account + "@nibash.bd")).hasSize(40);
        }
    }

    @Test
    void residentCannotReadAnotherBuilding() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");     // Gulshan
        long otherBuilding = buildingIdFor("committee2@nibash.bd");   // Banani

        // 404, never 403 — we do not confirm that a foreign building exists (spec §6.3).
        mvc.perform(get("/api/buildings/" + otherBuilding + "/").header("Authorization", "Token " + resident))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/units/?building_id=" + otherBuilding).header("Authorization", "Token " + resident))
                .andExpect(status().isNotFound());
    }

    @Test
    void residentOnlySeesOwnBuildingsUnits() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");
        long ownBuilding = buildingIdFor("resident1@nibash.bd");
        long expected = units.countByBuildingIdIn(java.util.List.of(ownBuilding));

        mvc.perform(get("/api/units/").header("Authorization", "Token " + resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value((int) expected));
    }

    // ---------------------------------------------------------------- role gating

    @Test
    void residentCannotCreateUnits() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");
        long building = buildingIdFor("resident1@nibash.bd");

        mvc.perform(post("/api/units/")
                        .header("Authorization", "Token " + resident)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"building":%d,"unit_number":"ZZ9"}
                                 """.formatted(building)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userAdministrationIsStrict() throws Exception {
        // Guards get no read access at all on a strict resource (spec §8.1).
        mvc.perform(get("/api/users/").header("Authorization", "Token " + tokenFor("guard1@nibash.bd")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/users/").header("Authorization", "Token " + tokenFor("admin1@nibash.bd")))
                .andExpect(status().isOk())
                // sensitive columns are absent by construction
                .andExpect(jsonPath("$.results[0].password_hash").doesNotExist())
                .andExpect(jsonPath("$.results[0].national_id").doesNotExist())
                .andExpect(jsonPath("$.results[0].dob").doesNotExist());
    }

    @Test
    void seedingIsBackOfficeOnly() throws Exception {
        mvc.perform(post("/api/seed/").header("Authorization", "Token " + tokenFor("committee1@nibash.bd")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Admin access required."));
    }

    // ---------------------------------------------------------------- directory privacy

    @Test
    void directoryWithholdsContactsForOptedOutResidents() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");

        // resident2 is seeded with opt_in = false, so their email and phone must be null.
        mvc.perform(get("/api/directory/").header("Authorization", "Token " + resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.opt_in == false)].email").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.results[?(@.opt_in == true)].email").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    void directorySearchMatchesName() throws Exception {
        mvc.perform(get("/api/directory/?search=Ayesha")
                        .header("Authorization", "Token " + tokenFor("resident1@nibash.bd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].name").value("Ayesha Rahman"));
    }

    // ---------------------------------------------------------------- seeder

    @Test
    void seederIsIdempotent() {
        ensureSeeded();
        long before = units.count();
        seeder.seed();
        seeder.seed();
        assertThat(units.count()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- pagination envelope

    @Test
    void listsUseTheDrfEnvelope() throws Exception {
        // Page 1 of 24 units: 20 rows, a `next` link, and `previous` explicitly null.
        // (`jsonPath(...).exists()` treats a JSON null as absent, hence the nullValue matcher.)
        mvc.perform(get("/api/units/").header("Authorization", "Token " + tokenFor("admin1@nibash.bd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(20))
                .andExpect(jsonPath("$.next").value(org.hamcrest.Matchers.containsString("page=2")))
                .andExpect(jsonPath("$.previous").value(org.hamcrest.Matchers.nullValue()));
    }
}
