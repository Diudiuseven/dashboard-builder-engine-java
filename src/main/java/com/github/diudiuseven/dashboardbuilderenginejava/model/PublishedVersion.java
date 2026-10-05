package com.github.diudiuseven.dashboardbuilderenginejava.model;

import java.time.Instant;

/**
 * 不可变的已发布版本快照。
 */
public record PublishedVersion(long version, ComponentNode root, Instant publishedAt, String publishedBy) {

    public PublishedVersion {
        // 快照在构造时即冻结，外部修改草稿不会影响已发布内容
        root = root.deepCopy();
    }

    @Override
    public ComponentNode root() {
        return root.deepCopy();
    }
}
