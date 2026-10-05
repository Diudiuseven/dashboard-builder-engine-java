package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.api.ActionResponse;
import com.github.diudiuseven.dashboardbuilderenginejava.api.ComponentMutationRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.DataSourceRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.DeleteComponentRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.PublishRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.RollbackRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.UpdateComponentRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Dashboard;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.DashboardDraftState;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.PublishedVersion;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.ValidationIssue;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ConflictException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ForbiddenException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.NotFoundException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.PublishFailureException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ValidationException;
import com.github.diudiuseven.dashboardbuilderenginejava.repository.DashboardRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestration and publishing engine.
 *
 * Concurrency: every mutating operation on one dashboard runs inside a
 * per-dashboard lock. The draft carries an {@code editVersion} optimistic
 * lock; every mutation must present the version the client last read, and a
 * mismatch (a concurrent editor got there first) is rejected with HTTP 409
 * instead of silently overwriting the other editor's change.
 *
 * Isolation: drafts and published versions live in separate collections and
 * are only ever exchanged through JSON deep copies, so editing the draft can
 * never alter a rendered version, and published snapshots stay immutable.
 */
@Service
public class DashboardService {

    private final DashboardRepository repository;
    private final ComponentTree tree;
    private final DraftValidator validator;
    private final JsonDeepCopier copier;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public DashboardService(DashboardRepository repository, ComponentTree tree,
                            DraftValidator validator, JsonDeepCopier copier) {
        this.repository = repository;
        this.tree = tree;
        this.validator = validator;
        this.copier = copier;
    }

    // --------------------------------------------------------------- reads

    public Dashboard createDashboard(String name, Role role, String actor) {
        require(role, Role.EDITOR);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Dashboard name must not be blank");
        }
        Dashboard dashboard = new Dashboard();
        dashboard.setId(UUID.randomUUID().toString());
        dashboard.setName(name.trim());
        dashboard.setDraft(new DashboardDraftState());
        return repository.save(dashboard);
    }

    public Dashboard getDashboard(String id, Role role) {
        require(role, Role.VIEWER);
        return load(id);
    }

    public DashboardDraftView getDraftView(String id, Role role) {
        require(role, Role.VIEWER);
        Dashboard dashboard = load(id);
        Long live = dashboard.getPublishedHistory().isEmpty()
                ? null
                : dashboard.getPublishedHistory().get(dashboard.getPublishedHistory().size() - 1).getVersion();
        return new DashboardDraftView(dashboard, live);
    }

    public PublishedVersion getPublished(String id, Role role) {
        require(role, Role.VIEWER);
        Dashboard dashboard = load(id);
        if (dashboard.getPublishedHistory().isEmpty()) {
            throw new NotFoundException("Dashboard " + id + " has no published version");
        }
        return dashboard.getPublishedHistory().get(dashboard.getPublishedHistory().size() - 1);
    }

    public PublishedVersion getPublishedVersion(String id, long version, Role role) {
        require(role, Role.VIEWER);
        Dashboard dashboard = load(id);
        return dashboard.getPublishedHistory().stream()
                .filter(v -> v.getVersion() == version)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Published version " + version + " of dashboard " + id + " does not exist"));
    }

    public List<PublishedVersion> getHistory(String id, Role role) {
        require(role, Role.VIEWER);
        return load(id).getPublishedHistory();
    }

    public List<ValidationIssue> validateDraft(String id, Role role) {
        require(role, Role.VIEWER);
        return validator.validate(load(id).getDraft());
    }

    // -------------------------------------------------------- draft edits

    public ActionResponse addComponent(String id, ComponentMutationRequest request, Role role) {
        require(role, Role.EDITOR);
        if (request == null || request.component() == null) {
            throw new IllegalArgumentException("component is required");
        }
        ComponentNode node = request.component();
        return mutate(id, request.expectedEditVersion(), dashboard -> {
            List<ValidationIssue> issues = new ArrayList<>();
            if (isBlank(node.getId())) {
                issues.add(ValidationIssue.structural("component.id", "Component has no id"));
            }
            if (isBlank(node.getType())) {
                issues.add(ValidationIssue.structural("component.type", "Component has no type"));
            }
            if (!isBlank(node.getId())
                    && tree.findById(dashboard.getDraft().getComponents(), node.getId()).isPresent()) {
                issues.add(ValidationIssue.structural("component.id",
                        "Component id already exists in the tree: " + node.getId()));
            }
            if (request.parentId() != null
                    && tree.findById(dashboard.getDraft().getComponents(), request.parentId()).isEmpty()) {
                issues.add(ValidationIssue.structural("parentId",
                        "Parent component does not exist: " + request.parentId()));
            }
            if (!issues.isEmpty()) {
                throw new ValidationException(issues);
            }
            if (request.parentId() == null) {
                dashboard.getDraft().getComponents().add(node);
            } else {
                // existence verified above
                ComponentNode parent = tree.findById(dashboard.getDraft().getComponents(), request.parentId()).get();
                parent.getChildren().add(node);
            }
        });
    }

    public ActionResponse updateComponent(String dashboardId, String componentId,
                                          UpdateComponentRequest request, Role role) {
        require(role, Role.EDITOR);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return mutate(dashboardId, request.expectedEditVersion(), dashboard -> {
            ComponentNode node = tree.findById(dashboard.getDraft().getComponents(), componentId)
                    .orElseThrow(() -> new NotFoundException(
                            "Component " + componentId + " does not exist in dashboard " + dashboardId));
            if (request.name() != null) {
                node.setName(request.name());
            }
            if (request.type() != null) {
                if (request.type().isBlank()) {
                    throw new ValidationException(List.of(ValidationIssue.structural(
                            "component.type", "Component type must not be blank")));
                }
                node.setType(request.type());
            }
            if (request.props() != null) {
                node.setProps(new java.util.LinkedHashMap<>(request.props()));
            }
            if (request.dataBindings() != null) {
                node.setDataBindings(new java.util.LinkedHashMap<>(request.dataBindings()));
            }
            if (request.componentRefs() != null) {
                node.setComponentRefs(new java.util.LinkedHashMap<>(request.componentRefs()));
            }
        });
    }

    public ActionResponse deleteComponent(String dashboardId, String componentId,
                                          DeleteComponentRequest request, Role role) {
        require(role, Role.EDITOR);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return mutate(dashboardId, request.expectedEditVersion(), dashboard -> {
            List<ComponentNode> roots = dashboard.getDraft().getComponents();
            ComponentNode victim = tree.findById(roots, componentId)
                    .orElseThrow(() -> new NotFoundException(
                            "Component " + componentId + " does not exist in dashboard " + dashboardId));
            List<String> subtree = tree.subtreeIds(victim);

            // Check references from components OUTSIDE the subtree into it:
            // deleting would leave dangling references, so reject unless the
            // caller forces the references to be cleared.
            List<ValidationIssue> blocking = new ArrayList<>();
            List<Runnable> cleanups = new ArrayList<>();
            for (ComponentNode node : allNodes(roots)) {
                if (subtree.contains(node.getId())) {
                    continue;
                }
                for (Map.Entry<String, String> ref : node.getComponentRefs().entrySet()) {
                    if (subtree.contains(ref.getValue())) {
                        blocking.add(ValidationIssue.structural(
                                "components(" + node.getId() + ").componentRefs." + ref.getKey(),
                                "Component '" + node.getId() + "' references component '"
                                        + ref.getValue() + "' inside the subtree being deleted"));
                        String prop = ref.getKey();
                        cleanups.add(() -> node.getComponentRefs().remove(prop));
                    }
                }
            }
            if (!blocking.isEmpty() && !request.force()) {
                throw new ValidationException(blocking);
            }

            // Detach victim + subtree and, when forced, clear incoming refs.
            tree.removeWithSubtree(roots, componentId);
            cleanups.forEach(Runnable::run);
        });
    }

    public ActionResponse addDataSource(String id, DataSourceRequest request, Role role) {
        require(role, Role.EDITOR);
        if (request == null || request.dataSource() == null) {
            throw new IllegalArgumentException("dataSource is required");
        }
        return mutate(id, request.expectedEditVersion(), dashboard -> {
            var ds = request.dataSource();
            List<ValidationIssue> issues = new ArrayList<>();
            if (isBlank(ds.getId())) {
                issues.add(ValidationIssue.structural("dataSource.id", "Data source has no id"));
            }
            if (isBlank(ds.getType())) {
                issues.add(ValidationIssue.structural("dataSource.type", "Data source has no type"));
            }
            if (!isBlank(ds.getId()) && dashboard.getDraft().getDataSources().containsKey(ds.getId())) {
                issues.add(ValidationIssue.structural("dataSource.id",
                        "Data source id already exists: " + ds.getId()));
            }
            if (!issues.isEmpty()) {
                throw new ValidationException(issues);
            }
            dashboard.getDraft().getDataSources().put(ds.getId(), ds);
        });
    }

    // ---------------------------------------------------- publish/rollback

    public ActionResponse publish(String id, PublishRequest request, Role role, String actor) {
        require(role, Role.PUBLISHER);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            Dashboard dashboard = load(id);
            long current = dashboard.getDraft().getEditVersion();
            if (request.expectedEditVersion() != current) {
                throw new ConflictException(
                        "Draft editVersion " + request.expectedEditVersion()
                                + " is stale; current version is " + current, current);
            }

            // Gate: refuse to publish an invalid page. No state is changed.
            List<ValidationIssue> issues = validator.validate(dashboard.getDraft());
            if (!issues.isEmpty()) {
                throw new ValidationException(issues);
            }

            // Build the whole snapshot on local state first. Any failure here
            // (including a simulated downstream failure) happens before the
            // history is touched, so the live version can never be seen in an
            // intermediate state.
            PublishedVersion snapshot = buildSnapshot(dashboard, actor);
            if (request.failAfterSnapshot()) {
                throw new PublishFailureException(
                        "Simulated publish failure after snapshot build for dashboard " + id);
            }

            dashboard.getPublishedHistory().add(snapshot);
            repository.save(dashboard);
            return ActionResponse.published(id, current, snapshot.getVersion());
        }
    }

    public ActionResponse rollback(String id, RollbackRequest request, Role role, String actor) {
        require(role, Role.PUBLISHER);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            Dashboard dashboard = load(id);
            PublishedVersion target = dashboard.getPublishedHistory().stream()
                    .filter(v -> v.getVersion() == request.version())
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Cannot roll back: published version " + request.version()
                                    + " of dashboard " + id + " does not exist"));

            // A rollback appends a NEW immutable version whose content is a
            // deep copy of the historical one. History is never rewritten and
            // the draft is left untouched, so rendering after rollback is
            // byte-for-byte the same page as when the target was published.
            PublishedVersion restored = new PublishedVersion();
            restored.setVersion(nextVersion(dashboard));
            restored.setPublishedAt(Instant.now());
            restored.setPublishedBy(actor);
            restored.setRestoredFromVersion(target.getVersion());
            restored.setComponents(copier.copy(target.getComponents(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ComponentNode>>() {}));
            restored.setDataSources(copier.copy(target.getDataSources(),
                    new com.fasterxml.jackson.core.type.TypeReference<
                            Map<String, com.github.diudiuseven.dashboardbuilderenginejava.domain.DataSourceDefinition>>() {}));

            dashboard.getPublishedHistory().add(restored);
            repository.save(dashboard);
            return ActionResponse.published(id, dashboard.getDraft().getEditVersion(),
                    restored.getVersion());
        }
    }

    // ------------------------------------------------------------- helpers

    private ActionResponse mutate(String id, long expected, java.util.function.Consumer<Dashboard> change) {
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            Dashboard dashboard = load(id);
            long current = dashboard.getDraft().getEditVersion();
            if (expected != current) {
                throw new ConflictException(
                        "Draft editVersion " + expected + " is stale; current version is " + current,
                        current);
            }
            change.accept(dashboard);
            dashboard.getDraft().setEditVersion(current + 1);
            repository.save(dashboard);
            return ActionResponse.edited(id, current + 1);
        }
    }

    private Dashboard load(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dashboard not found: " + id));
    }

    private PublishedVersion buildSnapshot(Dashboard dashboard, String actor) {
        DashboardDraftState draft = dashboard.getDraft();
        PublishedVersion snapshot = new PublishedVersion();
        snapshot.setVersion(nextVersion(dashboard));
        snapshot.setPublishedAt(Instant.now());
        snapshot.setPublishedBy(actor);
        // explicit deep copy: published content must never alias draft objects
        snapshot.setComponents(copier.copy(draft.getComponents(),
                new com.fasterxml.jackson.core.type.TypeReference<List<ComponentNode>>() {}));
        snapshot.setDataSources(copier.copy(draft.getDataSources(),
                new com.fasterxml.jackson.core.type.TypeReference<
                        Map<String, com.github.diudiuseven.dashboardbuilderenginejava.domain.DataSourceDefinition>>() {}));
        return snapshot;
    }

    private long nextVersion(Dashboard dashboard) {
        return dashboard.getPublishedHistory().isEmpty()
                ? 1L
                : dashboard.getPublishedHistory().get(dashboard.getPublishedHistory().size() - 1).getVersion() + 1;
    }

    private List<ComponentNode> allNodes(List<ComponentNode> roots) {
        List<ComponentNode> out = new ArrayList<>();
        java.util.Deque<ComponentNode> stack = new java.util.ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            ComponentNode n = stack.pop();
            out.add(n);
            n.getChildren().forEach(stack::push);
        }
        return out;
    }

    private void require(Role role, Role minimum) {
        if (role == null || role.ordinal() < minimum.ordinal()) {
            throw new ForbiddenException("Role " + role + " is not permitted; "
                    + minimum + " or higher is required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Draft plus the currently live published version number (null if never published). */
    public record DashboardDraftView(Dashboard dashboard, Long publishedVersion) {
    }
}
