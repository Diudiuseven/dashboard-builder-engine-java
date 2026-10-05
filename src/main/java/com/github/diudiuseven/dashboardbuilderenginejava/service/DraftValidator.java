package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.DashboardDraftState;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.DataSourceDefinition;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.ValidationIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a draft:
 * <ul>
 *   <li>structural integrity: null nodes, blank ids/types, duplicate ids;</li>
 *   <li>component -&gt; component references resolve to an existing node;</li>
 *   <li>component -&gt; data-source bindings resolve to an existing data source;</li>
 *   <li>the directed graph of component references contains no cycle
 *       (a self reference is a cycle of length one).</li>
 * </ul>
 * Every issue carries a JSON-ish path that locates the offending element.
 */
@Component
public class DraftValidator {

    private static final int WHITE = 0;
    private static final int GRAY = 1;
    private static final int BLACK = 2;

    private record Ref(String from, String to, String path) {
    }

    public List<ValidationIssue> validate(DashboardDraftState draft) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (draft == null) {
            issues.add(ValidationIssue.structural("$", "Draft is missing"));
            return issues;
        }

        Map<String, String> componentPaths = new HashMap<>();
        List<Ref> componentEdges = new ArrayList<>();
        List<Ref> dataSourceEdges = new ArrayList<>();

        List<ComponentNode> roots = draft.getComponents() == null
                ? List.of() : draft.getComponents();
        for (int i = 0; i < roots.size(); i++) {
            walk(roots.get(i), "components[" + i + "]", componentPaths,
                    componentEdges, dataSourceEdges, issues);
        }

        // data source definitions
        Map<String, DataSourceDefinition> sources = draft.getDataSources() == null
                ? Map.of() : draft.getDataSources();
        for (Map.Entry<String, DataSourceDefinition> entry : sources.entrySet()) {
            DataSourceDefinition ds = entry.getValue();
            if (ds == null || isBlank(ds.getId())) {
                issues.add(ValidationIssue.structural("dataSources." + entry.getKey(),
                        "Data source has no id"));
            } else if (!ds.getId().equals(entry.getKey())) {
                issues.add(ValidationIssue.structural("dataSources." + entry.getKey(),
                        "Data source map key '" + entry.getKey()
                                + "' does not match its id '" + ds.getId() + "'"));
            }
        }

        // missing component references
        Set<String> reportedMissingComponents = new HashSet<>();
        for (Ref edge : componentEdges) {
            if (!componentPaths.containsKey(edge.to())
                    && reportedMissingComponents.add(edge.path() + "->" + edge.to())) {
                issues.add(ValidationIssue.missingComponent(edge.path(), edge.to()));
            }
        }

        // missing data source references
        Set<String> reportedMissingSources = new HashSet<>();
        for (Ref edge : dataSourceEdges) {
            if (!sources.containsKey(edge.to())
                    && reportedMissingSources.add(edge.path() + "->" + edge.to())) {
                issues.add(ValidationIssue.missingDataSource(edge.path(), edge.to()));
            }
        }

        detectComponentCycles(componentEdges, componentPaths, issues);
        return issues;
    }

    private void walk(ComponentNode node, String path,
                      Map<String, String> componentPaths,
                      List<Ref> componentEdges,
                      List<Ref> dataSourceEdges,
                      List<ValidationIssue> issues) {
        if (node == null) {
            issues.add(ValidationIssue.structural(path, "Component is null"));
            return;
        }
        if (isBlank(node.getId())) {
            issues.add(ValidationIssue.structural(path, "Component has no id"));
        } else if (componentPaths.containsKey(node.getId())) {
            issues.add(ValidationIssue.structural(path,
                    "Duplicate component id '" + node.getId()
                            + "' (first seen at " + componentPaths.get(node.getId()) + ")"));
        } else {
            componentPaths.put(node.getId(), path);
        }
        if (isBlank(node.getType())) {
            issues.add(ValidationIssue.structural(path,
                    "Component '" + node.getId() + "' has no type"));
        }

        if (node.getComponentRefs() != null) {
            for (Map.Entry<String, String> ref : node.getComponentRefs().entrySet()) {
                String refPath = path + ".componentRefs." + ref.getKey();
                String target = ref.getValue();
                if (isBlank(target)) {
                    issues.add(ValidationIssue.structural(refPath,
                            "Empty component reference in property '" + ref.getKey() + "'"));
                } else {
                    componentEdges.add(new Ref(node.getId(), target, refPath));
                }
            }
        }
        if (node.getDataBindings() != null) {
            for (Map.Entry<String, String> binding : node.getDataBindings().entrySet()) {
                String bindingPath = path + ".dataBindings." + binding.getKey();
                String target = binding.getValue();
                if (isBlank(target)) {
                    issues.add(ValidationIssue.structural(bindingPath,
                            "Empty data source binding in property '" + binding.getKey() + "'"));
                } else {
                    dataSourceEdges.add(new Ref(node.getId(), target, bindingPath));
                }
            }
        }

        List<ComponentNode> kids = node.getChildren();
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                walk(kids.get(i), path + ".children[" + i + "]", componentPaths,
                        componentEdges, dataSourceEdges, issues);
            }
        }
    }

    /**
     * Coloured DFS over the directed component-reference graph. Only edges
     * whose both endpoints exist participate (missing edges are already
     * reported separately). Each distinct cycle is reported once.
     */
    private void detectComponentCycles(List<Ref> edges,
                                       Map<String, String> componentPaths,
                                       List<ValidationIssue> issues) {
        Map<String, List<Ref>> adjacency = new HashMap<>();
        for (String id : componentPaths.keySet()) {
            adjacency.put(id, new ArrayList<>());
        }
        for (Ref edge : edges) {
            if (componentPaths.containsKey(edge.from()) && componentPaths.containsKey(edge.to())) {
                adjacency.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            }
        }

        Map<String, Integer> colors = new HashMap<>();
        for (String id : adjacency.keySet()) {
            colors.put(id, WHITE);
        }
        List<String> stack = new ArrayList<>();
        Set<String> reportedCycles = new LinkedHashSet<>();

        for (String start : new ArrayList<>(adjacency.keySet())) {
            if (colors.get(start) == WHITE) {
                dfs(start, adjacency, colors, stack, issues, reportedCycles);
            }
        }
    }

    private void dfs(String u, Map<String, List<Ref>> adjacency, Map<String, Integer> colors,
                     List<String> stack, List<ValidationIssue> issues,
                     Set<String> reportedCycles) {
        colors.put(u, GRAY);
        stack.add(u);
        for (Ref edge : adjacency.getOrDefault(u, List.of())) {
            String v = edge.to();
            Integer c = colors.get(v);
            if (c == null || c == WHITE) {
                colors.putIfAbsent(v, WHITE);
                dfs(v, adjacency, colors, stack, issues, reportedCycles);
            } else if (c == GRAY) {
                int start = stack.indexOf(v);
                List<String> cycle = new ArrayList<>(stack.subList(start, stack.size()));
                cycle.add(v);
                String cyclePath = String.join(" -> ", cycle);
                if (reportedCycles.add(canonical(cycle))) {
                    issues.add(ValidationIssue.circularReference(edge.path(), cyclePath));
                }
            }
        }
        stack.remove(stack.size() - 1);
        colors.put(u, BLACK);
    }

    /** Canonical key for a cycle so the same loop is not reported twice. */
    private String canonical(List<String> cycle) {
        List<String> nodes = cycle.subList(0, cycle.size() - 1);
        int min = 0;
        for (int i = 1; i < nodes.size(); i++) {
            if (nodes.get(i).compareTo(nodes.get(min)) < 0) {
                min = i;
            }
        }
        List<String> rotated = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            rotated.add(nodes.get((min + i) % nodes.size()));
        }
        return String.join("->", rotated);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
