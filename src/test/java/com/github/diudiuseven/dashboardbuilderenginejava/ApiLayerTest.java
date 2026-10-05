package com.github.diudiuseven.dashboardbuilderenginejava;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层契约测试：角色头、状态码与错误体结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiLayerTest {

    @Autowired
    MockMvc mvc;

    private static String addOp(long base, String id) {
        return """
                {"type":"ADD","baseVersion":%d,"componentId":"%s",
                 "node":{"id":"%s","type":"chart"}}
                """.formatted(base, id, id);
    }

    @Test
    void fullHttpFlowWithRoleHeaders() throws Exception {
        String d = "api-flow";
        // 未带角色头默认为 VIEWER：读草稿被拒
        mvc.perform(get("/api/dashboards/{d}/draft", d))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ForbiddenException"));

        // 未知角色头同样被拒
        mvc.perform(get("/api/dashboards/{d}/draft", d).header("X-Role", "SUPERMAN"))
                .andExpect(status().isForbidden());

        // EDITOR 写草稿
        mvc.perform(post("/api/dashboards/{d}/draft/operations", d)
                        .header("X-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOp(0, "c1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftVersion").value(1));

        // 相同 baseVersion 重放 -> 409 冲突
        mvc.perform(post("/api/dashboards/{d}/draft/operations", d)
                        .header("X-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOp(0, "c2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ConflictException"));

        // EDITOR 发布被拒 -> 403
        mvc.perform(post("/api/dashboards/{d}/publish", d).header("X-Role", "EDITOR"))
                .andExpect(status().isForbidden());

        // ADMIN 发布成功，VIEWER 可读线上内容
        mvc.perform(post("/api/dashboards/{d}/publish", d).header("X-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedVersion").value(1));
        mvc.perform(get("/api/dashboards/{d}/published", d))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].id").value("c1"));
    }

    @Test
    void validationFailureReturns422WithLocatableErrors() throws Exception {
        String d = "api-validation";
        String badAdd = """
                {"type":"ADD","baseVersion":0,"componentId":"bad",
                 "node":{"id":"bad","type":"chart","dataSourceId":"ds-nope",
                          "dependencies":["missing"]}}
                """;
        mvc.perform(post("/api/dashboards/{d}/draft/operations", d)
                        .header("X-Role", "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badAdd))
                .andExpect(status().isOk());

        // 草稿校验接口可预览错误
        mvc.perform(post("/api/dashboards/{d}/draft/validate", d).header("X-Role", "EDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        // 发布 -> 422，错误体携带可定位信息
        mvc.perform(post("/api/dashboards/{d}/publish", d).header("X-Role", "ADMIN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].componentId").value("bad"));

        // 发布失败后线上无版本 -> 404
        mvc.perform(get("/api/dashboards/{d}/published", d))
                .andExpect(status().isNotFound());
    }
}
