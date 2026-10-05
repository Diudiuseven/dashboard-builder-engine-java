package com.github.diudiuseven.dashboardbuilderenginejava.model;

/**
 * 访问角色：VIEWER 只读已发布内容；EDITOR 可读写草稿；ADMIN 可发布与回滚。
 */
public enum Role {
    VIEWER,
    EDITOR,
    ADMIN;

    public boolean canReadPublished() {
        return true;
    }

    public boolean canReadDraft() {
        return this == EDITOR || this == ADMIN;
    }

    public boolean canEdit() {
        return this == EDITOR || this == ADMIN;
    }

    public boolean canPublish() {
        return this == ADMIN;
    }
}
