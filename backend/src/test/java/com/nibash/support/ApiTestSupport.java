package com.nibash.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nibash.seed.DemoSeeder;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared plumbing for the Week 5 and Week 6 integration suites: seed once, log in as a demo
 * account, and call the API as that user. Tokens are cached per email for the life of the class.
 */
public abstract class ApiTestSupport {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected DemoSeeder seeder;

    private static boolean seeded = false;
    private final Map<String, String> tokens = new HashMap<>();

    protected void ensureSeeded() {
        if (!seeded) {
            seeder.seed();
            seeded = true;
        }
    }

    protected String tokenFor(String email) throws Exception {
        ensureSeeded();
        String cached = tokens.get(email);
        if (cached != null) {
            return cached;
        }
        String body = mvc.perform(post("/api/auth/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, DemoSeeder.DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asString();
        tokens.put(email, token);
        return token;
    }

    protected JsonNode getJson(String path, String token) throws Exception {
        return call(get(path), token, null, 200);
    }

    protected JsonNode getJson(String path, String token, int expected) throws Exception {
        return call(get(path), token, null, expected);
    }

    protected JsonNode postJson(String path, String token, String payload, int expected) throws Exception {
        return call(post(path), token, payload, expected);
    }

    protected JsonNode patchJson(String path, String token, String payload, int expected) throws Exception {
        return call(patch(path), token, payload, expected);
    }

    protected JsonNode deleteJson(String path, String token, int expected) throws Exception {
        return call(delete(path), token, null, expected);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, String payload, int expected)
            throws Exception {
        if (token != null) {
            request.header("Authorization", "Token " + token);
        }
        if (payload != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(payload);
        }
        String body = mvc.perform(request)
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    protected long buildingId(String token) throws Exception {
        return getJson("/api/auth/me/", token).get("building").get("id").asLong();
    }

    /** The caller's own resident row in their home building. */
    protected long residentIdOf(String token) throws Exception {
        long userId = getJson("/api/auth/me/", token).get("user").get("id").asLong();
        long building = buildingId(token);
        for (JsonNode row : getJson("/api/residents/?building_id=" + building, token).get("results")) {
            if (row.get("user").asLong() == userId) {
                return row.get("id").asLong();
            }
        }
        throw new IllegalStateException("No resident row for user " + userId);
    }

    /** A value unique to this run, so re-running the suite against the same schema never collides. */
    protected static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }
}
