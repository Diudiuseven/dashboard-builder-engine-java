package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.model.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.model.ValidationError;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验组件树：组件间引用、组件与数据源引用、循环引用。
 * 所有错误都携带组件 id，便于定位。
 */
@Service
public class ValidationService {

    private final DataSourceRegistry dataSourceRegistry;

    public ValidationService(DataSourceRegistry dataSourceRegistry) {
        this.dataSourceRegistry = dataSourceRegistry;
    }

    /**
     * 校验整棵组件树，返回全部可定位错误（空列表表示通过）。
     */
    public List<ValidationError> validate(ComponentNode root) {
        List<ValidationError> errors = new ArrayList<>();
        Map<String, ComponentNode> byId = new HashMap<>();
        collect(root, byId, errors);

        // 1. 引用完整性：依赖的组件必须存在，绑定的数据源必须已注册
        for (ComponentNode node : byId.values()) {
            for (String dep : node.getDependencies()) {
                if (!byId.containsKey(dep)) {
                    errors.add(new ValidationError(node.getId(),
                            "references missing component '" + dep + "'"));
                }
            }
            String ds = node.getDataSourceId();
            if (ds != null && !ds.isBlank() && !dataSourceRegistry.exists(ds)) {
                errors.add(new ValidationError(node.getId(),
                        "binds unknown data source '" + ds + "'"));
            }
        }

        // 2. 循环引用检测（基于 dependencies 构成的有向图）
        detectCycles(byId, errors);

        return errors;
    }

    private void collect(ComponentNode node, Map<String, ComponentNode> byId, List<ValidationError> errors) {
        if (node.getId() == null || node.getId().isBlank()) {
            errors.add(new ValidationError("<unknown>", "component id must not be blank"));
        } else if (byId.putIfAbsent(node.getId(), node) != null) {
            errors.add(new ValidationError(node.getId(), "duplicate component id '" + node.getId() + "'"));
        }
        for (ComponentNode child : node.getChildren()) {
            collect(child, byId, errors);
        }
    }

    private void detectCycles(Map<String, ComponentNode> byId, List<ValidationError> errors) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        for (String id : byId.keySet()) {
            if (!visited.contains(id)) {
                dfs(id, byId, visited, inStack, path, errors);
            }
        }
    }

    private void dfs(String id, Map<String, ComponentNode> byId,
                     Set<String> visited, Set<String> inStack,
                     Deque<String> path, List<ValidationError> errors) {
        visited.add(id);
        inStack.add(id);
        path.addLast(id);
        ComponentNode node = byId.get(id);
        if (node != null) {
            for (String dep : node.getDependencies()) {
                if (!byId.containsKey(dep)) {
                    continue; // 缺失引用已在完整性检查中报告
                }
                if (inStack.contains(dep)) {
                    // 输出可定位的环路径，如 a -> b -> c -> a
                    List<String> cycle = new ArrayList<>();
                    boolean inCycle = false;
                    for (String p : path) {
                        if (p.equals(dep)) {
                            inCycle = true;
                        }
                        if (inCycle) {
                            cycle.add(p);
                        }
                    }
                    cycle.add(dep);
                    errors.add(new ValidationError(id,
                            "circular dependency detected: " + String.join(" -> ", cycle)));
                } else if (!visited.contains(dep)) {
                    dfs(dep, byId, visited, inStack, path, errors);
                }
            }
        }
        path.removeLast();
        inStack.remove(id);
    }
}
