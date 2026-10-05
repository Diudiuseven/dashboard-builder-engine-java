package com.github.diudiuseven.dashboardbuilderenginejava.web;

import com.github.diudiuseven.dashboardbuilderenginejava.api.ActionResponse;
import com.github.diudiuseven.dashboardbuilderenginejava.api.ComponentMutationRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.CreateDashboardRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.DataSourceRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.DeleteComponentRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.PublishRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.RollbackRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.UpdateComponentRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Dashboard;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.PublishedVersion;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.ValidationIssue;
import com.github.diudiuseven.dashboardbuilderenginejava.security.CurrentRole;
import com.github.diudiuseven.dashboardbuilderenginejava.security.RoleRequired;
import com.github.diudiuseven.dashboardbuilderenginejava.service.DashboardService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for the low-code dashboard engine.
 *
 * Authentication/authorization is handled by {@code RoleInterceptor}:
 * every request must carry {@code X-User-Role} and {@code @RoleRequired}
 * declares the minimum role per operation. {@code X-User-Name} is optional
 * and only used for audit fields.
 */
@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @PostMapping
    @RoleRequired(Role.EDITOR)
    public Dashboard create(@RequestBody CreateDashboardRequest request,
                            @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String actor) {
        return service.createDashboard(request == null ? null : request.name(),
                CurrentRole.get(), actor);
    }

    @GetMapping("/{id}")
    @RoleRequired(Role.VIEWER)
    public DashboardService.DashboardDraftView get(@PathVariable String id) {
        return service.getDraftView(id, CurrentRole.get());
    }

    @PostMapping("/{id}/components")
    @RoleRequired(Role.EDITOR)
    public ActionResponse addComponent(@PathVariable String id,
                                       @RequestBody ComponentMutationRequest request) {
        return service.addComponent(id, request, CurrentRole.get());
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/components/{componentId}")
    @RoleRequired(Role.EDITOR)
    public ActionResponse updateComponent(@PathVariable String id,
                                          @PathVariable String componentId,
                                          @RequestBody UpdateComponentRequest request) {
        return service.updateComponent(id, componentId, request, CurrentRole.get());
    }

    @DeleteMapping("/{id}/components/{componentId}")
    @RoleRequired(Role.EDITOR)
    public ActionResponse deleteComponent(@PathVariable String id,
                                          @PathVariable String componentId,
                                          @RequestBody DeleteComponentRequest request) {
        return service.deleteComponent(id, componentId, request, CurrentRole.get());
    }

    @PostMapping("/{id}/data-sources")
    @RoleRequired(Role.EDITOR)
    public ActionResponse addDataSource(@PathVariable String id,
                                        @RequestBody DataSourceRequest request) {
        return service.addDataSource(id, request, CurrentRole.get());
    }

    @GetMapping("/{id}/validate")
    @RoleRequired(Role.VIEWER)
    public List<ValidationIssue> validate(@PathVariable String id) {
        return service.validateDraft(id, CurrentRole.get());
    }

    @PostMapping("/{id}/publish")
    @RoleRequired(Role.PUBLISHER)
    public ActionResponse publish(@PathVariable String id,
                                  @RequestBody PublishRequest request,
                                  @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String actor) {
        return service.publish(id, request, CurrentRole.get(), actor);
    }

    @PostMapping("/{id}/rollback")
    @RoleRequired(Role.PUBLISHER)
    public ActionResponse rollback(@PathVariable String id,
                                   @RequestBody RollbackRequest request,
                                   @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String actor) {
        return service.rollback(id, request, CurrentRole.get(), actor);
    }

    @GetMapping("/{id}/published")
    @RoleRequired(Role.VIEWER)
    public PublishedVersion published(@PathVariable String id) {
        return service.getPublished(id, CurrentRole.get());
    }

    @GetMapping("/{id}/published/{version}")
    @RoleRequired(Role.VIEWER)
    public PublishedVersion publishedVersion(@PathVariable String id,
                                             @PathVariable long version) {
        return service.getPublishedVersion(id, version, CurrentRole.get());
    }

    @GetMapping("/{id}/versions")
    @RoleRequired(Role.VIEWER)
    public List<PublishedVersion> history(@PathVariable String id) {
        return service.getHistory(id, CurrentRole.get());
    }
}
