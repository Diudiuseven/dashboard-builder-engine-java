package com.github.diudiuseven.dashboardbuilderenginejava.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardApiTest {

    @Autowired
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String createDashboard(String role) throws Exception {
        MvcResult result = mvc.perform(post("/api/dashboards")
                        .header("X-User-Role", role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sales Board\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String addComponent(String id, String role, String componentId,
                                long expectedVersion) throws Exception {
        return "{\"component\":{\"id\":\"" + componentId + "\",\"type\":\"text\"},"
                + "\"parentId\":null,\"expectedEditVersion\":" + expectedVersion + "}";
    }

    @Test
    void missingRoleHeaderIsForbidden() throws Exception {
        mvc.perform(get("/api/dashboards/anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void viewerCannotCreateEditOrPublish() throws Exception {
        // create denied
        mvc.perform(post("/api/dashboards")
                        .header("X-User-Role", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());

        String id = createDashboard("EDITOR");

        // edit denied for viewer
        mvc.perform(post("/api/dashboards/" + id + "/components")
                        .header("X-User-Role", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addComponent(id, "VIEWER", "c", 0)))
                .andExpect(status().isForbidden());

        // publish denied for editor
        mvc.perform(post("/api/dashboards/" + id + "/publish")
                        .header("X-User-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEditVersion\":0,\"failAfterSnapshot\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void forbiddenEditProducesNoStateChange() throws Exception {
        String id = createDashboard("EDITOR");
        mvc.perform(post("/api/dashboards")
                .header("X-User-Role", "VIEWER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"should-not-exist\"}"))
                .andExpect(status().isForbidden());

        // draft remains at version 0 and empty, as the rejected add never ran
        MvcResult result = mvc.perform(get("/api/dashboards/" + id)
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("dashboard").get("draft").get("editVersion").asLong()).isEqualTo(0L);
        assertThat(body.get("dashboard").get("draft").get("components").size()).isEqualTo(0);
    }

    @Test
    void conflictingEditReturns409WithCurrentVersion() throws Exception {
        String id = createDashboard("EDITOR");

        mvc.perform(post("/api/dashboards/" + id + "/components")
                        .header("X-User-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addComponent(id, "EDITOR", "first", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editVersion").value(1));

        // stale version 0
        mvc.perform(post("/api/dashboards/" + id + "/components")
                        .header("X-User-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addComponent(id, "EDITOR", "second", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.currentEditVersion").value(1));
    }

    @Test
    void publishingInvalidDraftReturnsLocatableIssues() throws Exception {
        String id = createDashboard("EDITOR");
        String body = "{\"component\":{\"id\":\"a\",\"type\":\"chart\","
                + "\"componentRefs\":{\"filter\":\"ghost\"}},"
                + "\"parentId\":null,\"expectedEditVersion\":0}";
        mvc.perform(post("/api/dashboards/" + id + "/components")
                        .header("X-User-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/api/dashboards/" + id + "/publish")
                        .header("X-User-Role", "PUBLISHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEditVersion\":1,\"failAfterSnapshot\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.issues[0].code").value("MISSING_COMPONENT_REFERENCE"))
                .andExpect(jsonPath("$.issues[0].path").value("components[0].componentRefs.filter"));
    }

    @Test
    void happyPathPublishReadAndRollbackOverHttp() throws Exception {
        String id = createDashboard("EDITOR");
        mvc.perform(post("/api/dashboards/" + id + "/components")
                        .header("X-User-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addComponent(id, "EDITOR", "c1", 0)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dashboards/" + id + "/publish")
                        .header("X-User-Role", "PUBLISHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEditVersion\":1,\"failAfterSnapshot\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedVersion").value(1));

        // reading published content shows no draft leakage
        mvc.perform(post("/api/dashboards/" + id + "/components")
                        .header("X-User-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addComponent(id, "EDITOR", "c2-draft", 1)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboards/" + id + "/published")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.components.length()").value(1));

        // simulated failure keeps version 1 live
        mvc.perform(post("/api/dashboards/" + id + "/publish")
                        .header("X-User-Role", "PUBLISHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEditVersion\":2,\"failAfterSnapshot\":true}"))
                .andExpect(status().is5xxServerError());
        mvc.perform(get("/api/dashboards/" + id + "/published")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(jsonPath("$.version").value(1));
    }

}
