package com.github.diudiuseven.dashboardbuilderenginejava.store;

import com.github.diudiuseven.dashboardbuilderenginejava.model.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.model.PublishedVersion;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存存储：每个看板一份草稿 + 不可变发布版本列表 + 当前发布指针。
 * 草稿与发布版本完全隔离，草稿改动不会影响已发布快照。
 */
@Repository
public class DashboardStore {

    /** 单个看板的聚合状态。 */
    public static class DashboardState {
        public ComponentNode draftRoot = new ComponentNode("root", "page");
        public long draftVersion = 0L;
        public final Map<Long, PublishedVersion> publishedVersions = new ConcurrentHashMap<>();
        /** 当前生效的发布版本号，null 表示从未发布。 */
        public volatile Long currentPublishedVersion;
    }

    private final Map<String, DashboardState> states = new ConcurrentHashMap<>();

    public DashboardState stateOf(String dashboardId) {
        return states.computeIfAbsent(dashboardId, id -> new DashboardState());
    }

    public Optional<PublishedVersion> publishedVersion(String dashboardId, long version) {
        return Optional.ofNullable(stateOf(dashboardId).publishedVersions.get(version));
    }

    public List<PublishedVersion> publishedHistory(String dashboardId) {
        return stateOf(dashboardId).publishedVersions.values().stream()
                .sorted((a, b) -> Long.compare(a.version(), b.version()))
                .toList();
    }
}
