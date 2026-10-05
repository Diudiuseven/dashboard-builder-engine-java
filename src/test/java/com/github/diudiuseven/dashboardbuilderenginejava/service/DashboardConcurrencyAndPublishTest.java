package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.api.ComponentMutationRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.PublishRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.api.RollbackRequest;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Dashboard;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.PublishedVersion;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ConflictException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.PublishFailureException;
import com.github.diudiuseven.dashboardbuilderenginejava.repository.InMemoryDashboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardConcurrencyAndPublishTest {

    private DashboardService service;
    private InMemoryDashboardRepository repository;
    private ComponentTree tree;
    private String id;

    private ComponentNode node(String componentId, String type) {
        ComponentNode n = new ComponentNode();
        n.setId(componentId);
        n.setType(type);
        return n;
    }

    @BeforeEach
    void setUp() {
        JsonDeepCopier copier = new JsonDeepCopier();
        repository = new InMemoryDashboardRepository();
        tree = new ComponentTree();
        service = new DashboardService(repository, tree, new DraftValidator(), copier);
        id = service.createDashboard("d", Role.EDITOR, "t").getId();
    }

    @Test
    void twoEditorsStartingFromSameVersionExactlyOneWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    // both editors believe editVersion is 0
                    service.addComponent(id, new ComponentMutationRequest(
                            node("c" + idx, "text"), null, 0), Role.EDITOR);
                    successes.incrementAndGet();
                } catch (ConflictException e) {
                    conflicts.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.compareAndSet(null, t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected.get()).isNull();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        Dashboard d = service.getDashboard(id, Role.VIEWER);
        assertThat(d.getDraft().getEditVersion()).isEqualTo(1);
        assertThat(d.getDraft().getComponents()).hasSize(1);

        // conflict body tells the loser the current version to reload from
        long reported = 0;
        try {
            service.addComponent(id, new ComponentMutationRequest(
                    node("late", "text"), null, 0), Role.EDITOR);
        } catch (ConflictException e) {
            reported = e.getCurrentVersion();
        }
        assertThat(reported).isEqualTo(1);
    }

    @Test
    void serialEditorsSucceedAfterReloadingVersion() throws Exception {
        int n = 5;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // Each editor repeatedly tries to add its component, reloading the
        // current editVersion after every conflict. In the end every editor's
        // component must be present exactly once (no lost update).
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    boolean done = false;
                    while (!done) {
                        long version = service.getDashboard(id, Role.VIEWER)
                                .getDraft().getEditVersion();
                        try {
                            service.addComponent(id, new ComponentMutationRequest(
                                    node("node" + idx, "text"), null, version), Role.EDITOR);
                            done = true;
                        } catch (ConflictException ignored) {
                            // retry with fresh version
                        }
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();

        Dashboard d = service.getDashboard(id, Role.VIEWER);
        assertThat(d.getDraft().getComponents()).hasSize(n);
        assertThat(d.getDraft().getEditVersion()).isEqualTo(n);
    }

    @Test
    void failedPublishLeavesLiveVersionUntouched() {
        // version 1 is healthy and live
        service.addComponent(id, new ComponentMutationRequest(node("v1", "text"), null, 0), Role.EDITOR);
        service.publish(id, new PublishRequest(1, false), Role.PUBLISHER, "pub");

        // second draft, publish fails after snapshot was built
        service.addComponent(id, new ComponentMutationRequest(node("v2", "text"), null, 1), Role.EDITOR);
        assertThatThrownBy(() -> service.publish(id, new PublishRequest(2, true), Role.PUBLISHER, "pub"))
                .isInstanceOf(PublishFailureException.class);

        // live must still be exactly version 1
        PublishedVersion live = service.getPublished(id, Role.VIEWER);
        assertThat(live.getVersion()).isEqualTo(1);
        assertThat(live.getComponents()).extracting(ComponentNode::getId).containsExactly("v1");
        assertThat(service.getHistory(id, Role.VIEWER)).hasSize(1);

        // draft is intact, can be republished after the transient failure
        service.publish(id, new PublishRequest(2, false), Role.PUBLISHER, "pub");
        assertThat(service.getPublished(id, Role.VIEWER).getVersion()).isEqualTo(2);
    }

    @Test
    void rollbackRendersSameContentAsHistoricalVersion() {
        // publish v1: one component
        service.addComponent(id, new ComponentMutationRequest(node("only", "text"), null, 0), Role.EDITOR);
        service.publish(id, new PublishRequest(1, false), Role.PUBLISHER, "pub");
        PublishedVersion v1 = service.getPublishedVersion(id, 1, Role.VIEWER);

        // publish v2: two components
        service.addComponent(id, new ComponentMutationRequest(node("extra", "text"), null, 1), Role.EDITOR);
        service.publish(id, new PublishRequest(2, false), Role.PUBLISHER, "pub");

        // rollback to v1 -> new version 3 with identical content
        service.rollback(id, new RollbackRequest(1), Role.PUBLISHER, "pub");
        PublishedVersion v3 = service.getPublished(id, Role.VIEWER);
        assertThat(v3.getVersion()).isEqualTo(3);
        assertThat(v3.getRestoredFromVersion()).isEqualTo(1);
        assertThat(v3.getComponents()).extracting(ComponentNode::getId).containsExactly("only");
        assertThat(new JsonDeepCopier().copy(v3.getComponents(),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<ComponentNode>>() {}))
                .usingRecursiveComparison().isEqualTo(v1.getComponents());

        // history is append-only, draft untouched (still has both components)
        assertThat(service.getHistory(id, Role.VIEWER)).extracting(PublishedVersion::getVersion)
                .containsExactly(1L, 2L, 3L);
        assertThat(service.getDashboard(id, Role.VIEWER).getDraft().getComponents()).hasSize(2);
    }

    @Test
    void rollbackToUnknownVersionIsRejectedWithoutStateChange() {
        service.addComponent(id, new ComponentMutationRequest(node("only", "text"), null, 0), Role.EDITOR);
        service.publish(id, new PublishRequest(1, false), Role.PUBLISHER, "pub");

        assertThatThrownBy(() -> service.rollback(id, new RollbackRequest(99), Role.PUBLISHER, "pub"))
                .isInstanceOf(com.github.diudiuseven.dashboardbuilderenginejava.error.NotFoundException.class);
        assertThat(service.getHistory(id, Role.VIEWER)).hasSize(1);
        assertThat(service.getPublished(id, Role.VIEWER).getVersion()).isEqualTo(1);
    }
}
