package com.github.diudiuseven.dashboardbuilderenginejava;

import com.github.diudiuseven.dashboardbuilderenginejava.exception.ConflictException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.ForbiddenException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.NotFoundException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.ValidationFailedException;
import com.github.diudiuseven.dashboardbuilderenginejava.model.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.model.EditOperation;
import com.github.diudiuseven.dashboardbuilderenginejava.model.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.model.ValidationError;
import com.github.diudiuseven.dashboardbuilderenginejava.service.DashboardService;
import com.github.diudiuseven.dashboardbuilderenginejava.service.PublishService;
import com.github.diudiuseven.dashboardbuilderenginejava.service.ValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EngineServiceTest {

    @Autowired
    DashboardService dashboardService;
    @Autowired
    PublishService publishService;
    @Autowired
    ValidationService validationService;

    private static EditOperation op(EditOperation.Type type, long base, String componentId) {
        EditOperation op = new EditOperation();
        op.setType(type);
        op.setBaseVersion(base);
        op.setComponentId(componentId);
        return op;
    }

    private static EditOperation add(long base, String parentId, ComponentNode node) {
        EditOperation op = op(EditOperation.Type.ADD, base, node.getId());
        op.setParentId(parentId);
        op.setNode(node);
        return op;
    }

    private static ComponentNode node(String id, String type) {
        return new ComponentNode(id, type);
    }

    // ---------- 1. 增删改与嵌套布局 ----------

    @Test
    void addUpdateRemoveWithNestedLayout() {
        String d = "crud-basic";
        long v0 = dashboardService.getDraft(d, Role.EDITOR).draftVersion();

        ComponentNode row = node("row-1", "row");
        row.getChildren().add(node("chart-1", "chart"));
        long v1 = dashboardService.applyOperation(d, add(v0, null, row), Role.EDITOR);
        assertEquals(v0 + 1, v1);

        ComponentNode found = DashboardService.find(
                dashboardService.getDraft(d, Role.EDITOR).root(), "chart-1");
        assertNotNull(found, "nested child should exist");

        EditOperation update = op(EditOperation.Type.UPDATE, v1, "chart-1");
        ComponentNode patch = node("chart-1", "chart");
        patch.setProps(Map.of("title", "销售额"));
        patch.setDataSourceId("ds-sales");
        update.setNode(patch);
        long v2 = dashboardService.applyOperation(d, update, Role.EDITOR);
        assertEquals(v1 + 1, v2);
        assertEquals("ds-sales", DashboardService.find(
                dashboardService.getDraft(d, Role.EDITOR).root(), "chart-1").getDataSourceId());
    }

    @Test
    void removingParentRemovesWholeSubtree() {
        String d = "crud-subtree";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        ComponentNode container = node("box", "container");
        ComponentNode inner = node("inner", "container");
        inner.getChildren().add(node("leaf", "chart"));
        container.getChildren().add(inner);
        v = dashboardService.applyOperation(d, add(v, null, container), Role.EDITOR);

        dashboardService.applyOperation(d, op(EditOperation.Type.REMOVE, v, "box"), Role.EDITOR);
        var root = dashboardService.getDraft(d, Role.EDITOR).root();
        assertNull(DashboardService.find(root, "box"));
        assertNull(DashboardService.find(root, "inner"), "subtree must be removed with parent");
        assertNull(DashboardService.find(root, "leaf"), "subtree must be removed with parent");
        // 再删已不存在的节点 -> 明确报错而不是静默成功
        long vNow = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        assertThrows(NotFoundException.class, () -> dashboardService.applyOperation(
                d, op(EditOperation.Type.REMOVE, vNow, "leaf"), Role.EDITOR));
    }

    // ---------- 2. 草稿与发布隔离 ----------

    @Test
    void draftChangesNeverLeakIntoPublished() {
        String d = "isolation";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        ComponentNode chart = node("c1", "chart");
        chart.setProps(Map.of("title", "v1-title"));
        v = dashboardService.applyOperation(d, add(v, null, chart), Role.EDITOR);
        publishService.publish(d, Role.ADMIN, "tester");

        // 发布后继续修改草稿
        EditOperation update = op(EditOperation.Type.UPDATE, v, "c1");
        ComponentNode patch = node("c1", "chart");
        patch.setProps(Map.of("title", "draft-only-title"));
        update.setNode(patch);
        dashboardService.applyOperation(d, update, Role.EDITOR);

        ComponentNode published = publishService.renderPublished(d, Role.VIEWER);
        assertEquals("v1-title",
                DashboardService.find(published, "c1").getProps().get("title"),
                "published content must not see uncommitted draft changes");
    }

    // ---------- 3. 并发编辑冲突 ----------

    @Test
    void staleBaseVersionIsRejectedWithExplanation() {
        String d = "conflict-stale";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        dashboardService.applyOperation(d, add(v, null, node("a", "chart")), Role.EDITOR);

        // 编辑者 B 仍基于旧版本提交
        ConflictException ex = assertThrows(ConflictException.class,
                () -> dashboardService.applyOperation(d, add(v, null, node("b", "chart")), Role.EDITOR));
        assertTrue(ex.getMessage().contains("stale base version"), "error must explain the conflict");
        // 被拒绝的写入不得产生任何状态变更
        assertNull(DashboardService.find(dashboardService.getDraft(d, Role.EDITOR).root(), "b"));
    }

    @Test
    void concurrentEditorsExactlyOneWins() throws InterruptedException {
        String d = "conflict-race";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        int editors = 8;
        CountDownLatch ready = new CountDownLatch(editors);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        Thread[] threads = new Thread[editors];
        for (int i = 0; i < editors; i++) {
            int n = i;
            threads[i] = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    dashboardService.applyOperation(d, add(v, null, node("n" + n, "chart")), Role.EDITOR);
                    success.incrementAndGet();
                } catch (ConflictException e) {
                    conflict.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        for (Thread t : threads) {
            t.join(5000);
        }
        assertEquals(1, success.get(), "exactly one editor may win a same-baseVersion race");
        assertEquals(editors - 1, conflict.get(), "all others must get an explainable conflict");
        assertEquals(v + 1, dashboardService.getDraft(d, Role.EDITOR).draftVersion());
    }

    // ---------- 4. 引用校验 ----------

    @Test
    void validationReportsMissingAndCircularReferences() {
        ComponentNode root = node("root", "page");
        ComponentNode a = node("a", "chart");
        a.setDependencies(List.of("b", "ghost")); // ghost 不存在
        a.setDataSourceId("ds-not-exists");       // 未知数据源
        ComponentNode b = node("b", "chart");
        b.setDependencies(List.of("a"));          // a <-> b 循环
        root.getChildren().add(a);
        root.getChildren().add(b);

        List<ValidationError> errors = validationService.validate(root);
        assertTrue(errors.stream().anyMatch(e -> e.componentId().equals("a")
                && e.message().contains("ghost")), "missing component ref must be located");
        assertTrue(errors.stream().anyMatch(e -> e.componentId().equals("a")
                && e.message().contains("ds-not-exists")), "unknown data source must be located");
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("circular dependency")),
                "cycle must be detected");
    }

    // ---------- 5. 发布失败原子性 + 回滚 ----------

    @Test
    void failedPublishLeavesOnlineVersionUntouched() {
        String d = "publish-atomic";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        ComponentNode ok = node("ok", "chart");
        ok.setDataSourceId("ds-sales");
        v = dashboardService.applyOperation(d, add(v, null, ok), Role.EDITOR);
        long v1 = publishService.publish(d, Role.ADMIN, "tester");

        // 制造非法草稿：引用不存在的数据源
        EditOperation bad = op(EditOperation.Type.UPDATE, v, "ok");
        ComponentNode patch = node("ok", "chart");
        patch.setDataSourceId("ds-broken");
        bad.setNode(patch);
        dashboardService.applyOperation(d, bad, Role.EDITOR);

        assertThrows(ValidationFailedException.class,
                () -> publishService.publish(d, Role.ADMIN, "tester"));
        // 线上仍是 v1，无中间状态
        ComponentNode online = publishService.renderPublished(d, Role.VIEWER);
        assertEquals("ds-sales", DashboardService.find(online, "ok").getDataSourceId());
        assertEquals(1, publishService.history(d, Role.VIEWER).size(),
                "failed publish must not create any version");
        assertEquals(v1, publishService.history(d, Role.VIEWER).get(0).version());
    }

    @Test
    void rollbackRestoresExactHistoricalRendering() {
        String d = "rollback";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        ComponentNode c = node("c", "chart");
        c.setProps(Map.of("title", "first"));
        v = dashboardService.applyOperation(d, add(v, null, c), Role.EDITOR);
        long v1 = publishService.publish(d, Role.ADMIN, "tester");
        ComponentNode renderingV1 = publishService.renderPublished(d, Role.VIEWER);

        EditOperation update = op(EditOperation.Type.UPDATE, v, "c");
        ComponentNode patch = node("c", "chart");
        patch.setProps(Map.of("title", "second"));
        update.setNode(patch);
        dashboardService.applyOperation(d, update, Role.EDITOR);
        publishService.publish(d, Role.ADMIN, "tester");
        assertEquals("second", DashboardService.find(
                publishService.renderPublished(d, Role.VIEWER), "c").getProps().get("title"));

        long v3 = publishService.rollback(d, v1, Role.ADMIN, "ops");
        assertNotEquals(v1, v3, "rollback creates a new version, history stays append-only");
        ComponentNode afterRollback = publishService.renderPublished(d, Role.VIEWER);
        assertEquals("first", DashboardService.find(afterRollback, "c").getProps().get("title"));
        // 与当时渲染结果逐节点一致
        assertEquals(DashboardService.find(renderingV1, "c").getProps(),
                DashboardService.find(afterRollback, "c").getProps());
        assertEquals(3, publishService.history(d, Role.VIEWER).size());
    }

    @Test
    void rollbackToUnknownVersionFailsWithoutStateChange() {
        String d = "rollback-missing";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        dashboardService.applyOperation(d, add(v, null, node("x", "chart")), Role.EDITOR);
        publishService.publish(d, Role.ADMIN, "tester");
        assertThrows(NotFoundException.class,
                () -> publishService.rollback(d, 999, Role.ADMIN, "ops"));
        assertEquals(1, publishService.history(d, Role.VIEWER).size());
    }

    // ---------- 6. 角色权限 ----------

    @Test
    void unauthorizedOperationsAreRejectedWithoutStateChange() {
        String d = "rbac";
        long v = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        dashboardService.applyOperation(d, add(v, null, node("c", "chart")), Role.EDITOR);
        publishService.publish(d, Role.ADMIN, "tester");
        long draftBefore = dashboardService.getDraft(d, Role.EDITOR).draftVersion();
        int historyBefore = publishService.history(d, Role.VIEWER).size();

        // VIEWER：不能读草稿、不能编辑、不能发布、不能回滚
        assertThrows(ForbiddenException.class, () -> dashboardService.getDraft(d, Role.VIEWER));
        assertThrows(ForbiddenException.class, () -> dashboardService.applyOperation(
                d, add(draftBefore, null, node("evil", "chart")), Role.VIEWER));
        assertThrows(ForbiddenException.class, () -> publishService.publish(d, Role.VIEWER, "viewer"));
        assertThrows(ForbiddenException.class, () -> publishService.rollback(d, 1, Role.VIEWER, "viewer"));
        // EDITOR：不能发布与回滚
        assertThrows(ForbiddenException.class, () -> publishService.publish(d, Role.EDITOR, "editor"));
        assertThrows(ForbiddenException.class, () -> publishService.rollback(d, 1, Role.EDITOR, "editor"));

        // 所有越权请求均不得产生状态变更
        assertEquals(draftBefore, dashboardService.getDraft(d, Role.EDITOR).draftVersion());
        assertEquals(historyBefore, publishService.history(d, Role.VIEWER).size());
        assertNull(DashboardService.find(dashboardService.getDraft(d, Role.EDITOR).root(), "evil"));
    }
}
