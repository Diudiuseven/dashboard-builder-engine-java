package com.github.diudiuseven.dashboardbuilderenginejava.controller;

import com.github.diudiuseven.dashboardbuilderenginejava.exception.ForbiddenException;
import com.github.diudiuseven.dashboardbuilderenginejava.model.EditOperation;
import com.github.diudiuseven.dashboardbuilderenginejava.model.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.service.DashboardService;
import com.github.diudiuseven.dashboardbuilderenginejava.service.PublishService;
import com.github.diudiuseven.dashboardbuilderenginejava.service.ValidationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 看板编排与发布接口。角色通过请求头 X-Role 传入（VIEWER/EDITOR/ADMIN）。
 */
@RestController
@RequestMapping("/api/dashboards/{dashboardId}")
public class DashboardController {

    private final DashboardService dashboardService;
    private final PublishService publishService;
    private final ValidationService validationService;

    public DashboardController(DashboardService dashboardService, PublishService publishService,
                               ValidationService validationService) {
        this.dashboardService = dashboardService;
        this.publishService = publishService;
        this.validationService = validationService;
    }

    static Role roleOf(String header) {
        if (header == null || header.isBlank()) {
            return Role.VIEWER;
        }
        try {
            return Role.valueOf(header.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("unknown role: " + header);
        }
    }

    @GetMapping("/draft")
    public Object getDraft(@PathVariable String dashboardId,
                           @RequestHeader(value = "X-Role", required = false) String role) {
        return dashboardService.getDraft(dashboardId, roleOf(role));
    }

    @PostMapping("/draft/operations")
    public Object applyOperation(@PathVariable String dashboardId,
                                 @RequestBody EditOperation operation,
                                 @RequestHeader(value = "X-Role", required = false) String role) {
        long newVersion = dashboardService.applyOperation(dashboardId, operation, roleOf(role));
        return Map.of("draftVersion", newVersion);
    }

    @PostMapping("/draft/validate")
    public Object validateDraft(@PathVariable String dashboardId,
                                @RequestHeader(value = "X-Role", required = false) String role) {
        var draft = dashboardService.getDraft(dashboardId, roleOf(role));
        var errors = validationService.validate(draft.root());
        return Map.of("valid", errors.isEmpty(), "errors", errors);
    }

    @GetMapping("/published")
    public Object renderPublished(@PathVariable String dashboardId,
                                  @RequestHeader(value = "X-Role", required = false) String role) {
        return publishService.renderPublished(dashboardId, roleOf(role));
    }

    @GetMapping("/published/history")
    public Object history(@PathVariable String dashboardId,
                          @RequestHeader(value = "X-Role", required = false) String role) {
        return publishService.history(dashboardId, roleOf(role));
    }

    @PostMapping("/publish")
    public Object publish(@PathVariable String dashboardId,
                          @RequestHeader(value = "X-Role", required = false) String role,
                          @RequestHeader(value = "X-Operator", required = false, defaultValue = "anonymous") String operator) {
        long version = publishService.publish(dashboardId, roleOf(role), operator);
        return Map.of("publishedVersion", version);
    }

    @PostMapping("/rollback/{version}")
    public Object rollback(@PathVariable String dashboardId,
                           @PathVariable long version,
                           @RequestHeader(value = "X-Role", required = false) String role,
                           @RequestHeader(value = "X-Operator", required = false, defaultValue = "anonymous") String operator) {
        long newVersion = publishService.rollback(dashboardId, version, roleOf(role), operator);
        return Map.of("publishedVersion", newVersion);
    }
}
