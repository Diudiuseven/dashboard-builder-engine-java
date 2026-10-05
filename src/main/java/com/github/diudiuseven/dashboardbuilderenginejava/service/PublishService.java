package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.exception.ForbiddenException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.NotFoundException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.ValidationFailedException;
import com.github.diudiuseven.dashboardbuilderenginejava.model.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.model.PublishedVersion;
import com.github.diudiuseven.dashboardbuilderenginejava.model.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.model.ValidationError;
import com.github.diudiuseven.dashboardbuilderenginejava.store.DashboardStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 发布与回滚服务。
 * 发布流程：校验 -> 生成不可变快照 -> 原子切换当前版本指针。
 * 任一步骤失败都会抛异常且当前线上版本保持不变，不存在中间状态。
 * 回滚采用"以历史快照内容追加新版本"的方式，历史记录只增不改，
 * 回滚后渲染结果与目标历史版本完全一致。
 */
@Service
public class PublishService {

    private final DashboardStore store;
    private final ValidationService validationService;

    public PublishService(DashboardStore store, ValidationService validationService) {
        this.store = store;
        this.validationService = validationService;
    }

    /** 发布当前草稿为新版本，返回新版本号。 */
    public long publish(String dashboardId, Role role, String operator) {
        require(role.canPublish(), "role " + role + " cannot publish");
        DashboardStore.DashboardState state = store.stateOf(dashboardId);
        synchronized (state) {
            // 第 1 步：校验，失败则抛出，状态完全不变
            List<ValidationError> errors = validationService.validate(state.draftRoot);
            if (!errors.isEmpty()) {
                throw new ValidationFailedException(errors);
            }
            // 第 2 步：构造不可变快照（构造失败同样不影响线上版本）
            long newVersion = state.publishedVersions.keySet().stream()
                    .mapToLong(Long::longValue).max().orElse(0L) + 1;
            PublishedVersion snapshot = new PublishedVersion(
                    newVersion, state.draftRoot, Instant.now(), operator);
            // 第 3 步：原子提交——先落快照再切指针，两步之间无外部可见状态
            state.publishedVersions.put(newVersion, snapshot);
            state.currentPublishedVersion = newVersion;
            return newVersion;
        }
    }

    /** 回滚到指定历史版本：以该版本内容生成新版本并置为当前版本。 */
    public long rollback(String dashboardId, long targetVersion, Role role, String operator) {
        require(role.canPublish(), "role " + role + " cannot rollback");
        DashboardStore.DashboardState state = store.stateOf(dashboardId);
        synchronized (state) {
            PublishedVersion target = state.publishedVersions.get(targetVersion);
            if (target == null) {
                throw new NotFoundException("published version " + targetVersion
                        + " not found for dashboard '" + dashboardId + "'");
            }
            long newVersion = state.publishedVersions.keySet().stream()
                    .mapToLong(Long::longValue).max().orElse(0L) + 1;
            // PublishedVersion 构造时会深拷贝，渲染结果与目标版本当时一致
            PublishedVersion restored = new PublishedVersion(
                    newVersion, target.root(), Instant.now(),
                    operator + " (rollback to v" + targetVersion + ")");
            state.publishedVersions.put(newVersion, restored);
            state.currentPublishedVersion = newVersion;
            return newVersion;
        }
    }

    /** 读取当前线上渲染内容（仅已发布快照，绝不含草稿改动）。 */
    public ComponentNode renderPublished(String dashboardId, Role role) {
        require(role.canReadPublished(), "role " + role + " cannot read published content");
        DashboardStore.DashboardState state = store.stateOf(dashboardId);
        Long current = state.currentPublishedVersion;
        if (current == null) {
            throw new NotFoundException("dashboard '" + dashboardId + "' has no published version");
        }
        return state.publishedVersions.get(current).root();
    }

    public List<PublishedVersion> history(String dashboardId, Role role) {
        require(role.canReadPublished(), "role " + role + " cannot read publish history");
        return store.publishedHistory(dashboardId);
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new ForbiddenException(message);
        }
    }
}
