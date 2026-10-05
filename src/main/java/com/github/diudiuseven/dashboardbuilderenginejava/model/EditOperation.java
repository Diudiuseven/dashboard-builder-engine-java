package com.github.diudiuseven.dashboardbuilderenginejava.model;

/**
 * 一次草稿编辑操作。baseVersion 用于乐观并发控制：
 * 只有当操作基于的草稿版本与当前草稿版本一致时才会被接受。
 */
public class EditOperation {

    public enum Type {
        ADD,
        UPDATE,
        REMOVE,
        MOVE
    }

    private Type type;
    /** 客户端提交时所基于的草稿版本号。 */
    private long baseVersion;
    /** 目标组件 id（ADD 时为待添加节点的 id）。 */
    private String componentId;
    /** ADD/MOVE 时的父组件 id，null 表示挂到根节点。 */
    private String parentId;
    /** ADD/MOVE 时在父组件 children 中的插入位置，null 表示追加到末尾。 */
    private Integer index;
    /** ADD/UPDATE 时携带的节点数据。 */
    private ComponentNode node;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(long baseVersion) {
        this.baseVersion = baseVersion;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public ComponentNode getNode() {
        return node;
    }

    public void setNode(ComponentNode node) {
        this.node = node;
    }
}
