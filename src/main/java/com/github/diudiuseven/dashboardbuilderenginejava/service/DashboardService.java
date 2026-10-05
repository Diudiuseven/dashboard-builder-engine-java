package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.exception.ConflictException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.ForbiddenException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.NotFoundException;
import com.github.diudiuseven.dashboardbuilderenginejava.model.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.model.EditOperation;
import com.github.diudiuseven.dashboardbuilderenginejava.model.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.store.DashboardStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 草稿编排服务：组件增删改、嵌套布局、乐观并发控制。
 * 所有编辑操作在单个看板状态上串行化，baseVersion 不一致即拒绝，绝不静默覆盖。
 */
@Service
public class DashboardService {

    private final DashboardStore store;

    public DashboardService(DashboardStore store) {
        this.store = store;
    }

    /** 读取草稿（含当前草稿版本号）。 */
    public DraftView getDraft(String dashboardId, Role role) {
        require(role.canReadDraft(), "role " + role + " cannot read draft");
        DashboardStore.DashboardState state = store.stateOf(dashboardId);
        synchronized (state) {
            return new DraftView(state.draftVersion, state.draftRoot.deepCopy());
        }
    }

    /** 应用一次编辑操作，返回新的草稿版本号。 */
    public long applyOperation(String dashboardId, EditOperation operation, Role role) {
        require(role.canEdit(), "role " + role + " cannot edit draft");
        if (operation == null || operation.getType() == null) {
            throw new IllegalArgumentException("operation type is required");
        }
        DashboardStore.DashboardState state = store.stateOf(dashboardId);
        synchronized (state) {
            if (operation.getBaseVersion() != state.draftVersion) {
                throw new ConflictException("stale base version: operation based on draft v"
                        + operation.getBaseVersion() + " but current draft is v" + state.draftVersion
                        + "; refresh the draft and retry");
            }
            switch (operation.getType()) {
                case ADD -> applyAdd(state, operation);
                case UPDATE -> applyUpdate(state, operation);
                case REMOVE -> applyRemove(state, operation);
                case MOVE -> applyMove(state, operation);
            }
            state.draftVersion++;
            return state.draftVersion;
        }
    }

    private void applyAdd(DashboardStore.DashboardState state, EditOperation op) {
        ComponentNode node = op.getNode();
        if (node == null || node.getId() == null || node.getId().isBlank()) {
            throw new IllegalArgumentException("ADD requires a node with non-blank id");
        }
        if (find(state.draftRoot, node.getId()) != null) {
            throw new ConflictException("component '" + node.getId() + "' already exists in draft");
        }
        ComponentNode parent = resolveParent(state, op.getParentId());
        insertAt(parent.getChildren(), node, op.getIndex());
    }

    private void applyUpdate(DashboardStore.DashboardState state, EditOperation op) {
        ComponentNode existing = requireComponent(state, op.getComponentId());
        ComponentNode patch = op.getNode();
        if (patch == null) {
            throw new IllegalArgumentException("UPDATE requires a node payload");
        }
        if (patch.getType() != null) {
            existing.setType(patch.getType());
        }
        existing.setProps(patch.getProps());
        existing.setDependencies(patch.getDependencies());
        existing.setDataSourceId(patch.getDataSourceId());
    }

    private void applyRemove(DashboardStore.DashboardState state, EditOperation op) {
        String id = op.getComponentId();
        if ("root".equals(id)) {
            throw new IllegalArgumentException("cannot remove the root node");
        }
        // 父组件删除时整个子树一并移除，树结构天然保证无悬空子节点；
        // 其他组件对该子树的 dependencies 引用由发布前校验负责暴露。
        if (!removeFromParent(state.draftRoot, id)) {
            throw new NotFoundException("component '" + id + "' not found in draft");
        }
    }

    private void applyMove(DashboardStore.DashboardState state, EditOperation op) {
        String id = op.getComponentId();
        if ("root".equals(id)) {
            throw new IllegalArgumentException("cannot move the root node");
        }
        ComponentNode node = requireComponent(state, id);
        ComponentNode newParent = resolveParent(state, op.getParentId());
        if (find(node, newParent.getId()) != null) {
            throw new ConflictException("cannot move component '" + id + "' into its own subtree");
        }
        removeFromParent(state.draftRoot, id);
        insertAt(newParent.getChildren(), node, op.getIndex());
    }

    private ComponentNode resolveParent(DashboardStore.DashboardState state, String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return state.draftRoot;
        }
        ComponentNode parent = find(state.draftRoot, parentId);
        if (parent == null) {
            throw new NotFoundException("parent component '" + parentId + "' not found in draft");
        }
        return parent;
    }

    private ComponentNode requireComponent(DashboardStore.DashboardState state, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("componentId is required");
        }
        ComponentNode node = find(state.draftRoot, id);
        if (node == null) {
            throw new NotFoundException("component '" + id + "' not found in draft");
        }
        return node;
    }

    public static ComponentNode find(ComponentNode node, String id) {
        if (node.getId().equals(id)) {
            return node;
        }
        for (ComponentNode child : node.getChildren()) {
            ComponentNode found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean removeFromParent(ComponentNode parent, String id) {
        if (parent.getChildren().removeIf(c -> c.getId().equals(id))) {
            return true;
        }
        for (ComponentNode child : parent.getChildren()) {
            if (removeFromParent(child, id)) {
                return true;
            }
        }
        return false;
    }

    private static void insertAt(List<ComponentNode> children, ComponentNode node, Integer index) {
        if (index == null || index < 0 || index > children.size()) {
            children.add(node);
        } else {
            children.add(index, node);
        }
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new ForbiddenException(message);
        }
    }

    public record DraftView(long draftVersion, ComponentNode root) {
    }
}
