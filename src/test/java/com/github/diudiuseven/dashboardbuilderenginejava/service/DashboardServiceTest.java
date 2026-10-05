package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.api.ComponentMutationRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.DeleteComponentRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.PublishRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Dashboard;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.PublishedVersion;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ValidationException;
import com.github.diudiuseven.dashboardbuilderenginejava.repository.InMemoryDashboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardServiceTest {

    private DashboardService service;
    private InMemoryDashboardRepository repository;
    private ComponentTree tree;
    private String dashboardId;

    private ComponentNode node(String id, String type) {
        ComponentNode n = new ComponentNode();
        n.setId(id);
        n.setType(type);
        return n;
    }

    @BeforeEach
    void setUp() {
        JsonDeepCopier copier = new JsonDeepCopier();
        repository = new InMemoryDashboardRepository();
        tree = new ComponentTree();
        service = new DashboardService(repository, tree, new DraftValidator(), copier);
        dashboardId = service.createDashboard("d", Role.EDITOR, "tester").getId();
    }

    @Test
    void deletingParentRemovesWholeSubtree() {
        ComponentNode parent = node("parent", "container");
        parent.getChildren().add(node("child", "text"));
        service.addComponent(dashboardId,
                new ComponentMutationRequest(parent, null, 0), Role.EDITOR);

        service.deleteComponent(dashboardId, "parent",
                new DeleteComponentRequest(1, false), Role.EDITOR);

        Dashboard d = service.getDashboard(dashboardId, Role.VIEWER);
        assertThat(d.getDraft().getComponents()).isEmpty();
        assertThat(tree.findById(d.getDraft().getComponents(), "child")).isEmpty();
    }

    @Test
    void deletingReferencedComponentIsRejectedToPreventDanglingRef() {
        ComponentNode target = node("target", "filter");
        ComponentNode holder = node("holder", "chart");
        holder.getComponentRefs().put("filter", "target");
        service.addComponent(dashboardId,
                new ComponentMutationRequest(target, null, 0), Role.EDITOR);
        service.addComponent(dashboardId,
                new ComponentMutationRequest(holder, null, 1), Role.EDITOR);

        assertThatThrownBy(() -> service.deleteComponent(dashboardId, "target",
                new DeleteComponentRequest(2, false), Role.EDITOR))
                .isInstanceOf(ValidationException.class);

        // still present: nothing was deleted
        Dashboard d = service.getDashboard(dashboardId, Role.VIEWER);
        assertThat(tree.findById(d.getDraft().getComponents(), "target")).isPresent();
    }

    @Test
    void forcedDeleteClearsIncomingReferencesAndRemovesSubtree() {
        ComponentNode target = node("target", "filter");
        ComponentNode holder = node("holder", "chart");
        holder.getComponentRefs().put("filter", "target");
        service.addComponent(dashboardId,
                new ComponentMutationRequest(target, null, 0), Role.EDITOR);
        service.addComponent(dashboardId,
                new ComponentMutationRequest(holder, null, 1), Role.EDITOR);

        service.deleteComponent(dashboardId, "target",
                new DeleteComponentRequest(2, true), Role.EDITOR);

        Dashboard d = service.getDashboard(dashboardId, Role.VIEWER);
        ComponentNode holderNow = tree.findById(d.getDraft().getComponents(), "holder").orElseThrow();
        assertThat(holderNow.getComponentRefs()).doesNotContainKey("filter");
        assertThat(tree.findById(d.getDraft().getComponents(), "target")).isEmpty();
    }

    @Test
    void draftEditsDoNotChangePublishedVersion() {
        service.addComponent(dashboardId,
                new ComponentMutationRequest(node("c1", "text"), null, 0), Role.EDITOR);
        service.publish(dashboardId, new PublishRequest(1, false), Role.PUBLISHER, "pub");
        PublishedVersion first = service.getPublished(dashboardId, Role.VIEWER);
        assertThat(first.getComponents()).hasSize(1);

        // unpublished draft change
        service.addComponent(dashboardId,
                new ComponentMutationRequest(node("c2", "text"), null, 1), Role.EDITOR);

        PublishedVersion stillFirst = service.getPublished(dashboardId, Role.VIEWER);
        assertThat(stillFirst.getVersion()).isEqualTo(1);
        assertThat(stillFirst.getComponents()).hasSize(1);
        assertThat(stillFirst.getComponents().get(0).getId()).isEqualTo("c1");
        // mutating the returned draft object must not touch stored state either
        stillFirst.getComponents().add(node("injected", "hacker"));
        assertThat(service.getPublished(dashboardId, Role.VIEWER).getComponents()).hasSize(1);
    }

    @Test
    void publishingInvalidDraftIsRejected() {
        ComponentNode broken = node("a", "chart");
        broken.getComponentRefs().put("missing", "ghost");
        service.addComponent(dashboardId,
                new ComponentMutationRequest(broken, null, 0), Role.EDITOR);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.publish(dashboardId, new PublishRequest(1, false), Role.PUBLISHER, "pub"))
                .isInstanceOf(ValidationException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.getPublished(dashboardId, Role.VIEWER))
                .isInstanceOf(com.github.diudiuseven.dashboardbuilderenginejava.error.NotFoundException.class);
    }
}
