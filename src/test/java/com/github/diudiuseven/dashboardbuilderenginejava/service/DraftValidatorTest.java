package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.ComponentNode;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.DashboardDraftState;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.DataSourceDefinition;
import com.github.diudiuseven.dashboardbuilderenginejava.domain.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DraftValidatorTest {

    private final DraftValidator validator = new DraftValidator();

    private ComponentNode node(String id, String type) {
        ComponentNode n = new ComponentNode();
        n.setId(id);
        n.setType(type);
        return n;
    }

    private DataSourceDefinition ds(String id) {
        DataSourceDefinition d = new DataSourceDefinition();
        d.setId(id);
        d.setType("REST");
        return d;
    }

    @Test
    void validDraftHasNoIssues() {
        ComponentNode a = node("a", "chart");
        a.getDataBindings().put("series", "sales");
        ComponentNode b = node("b", "filter");
        a.getComponentRefs().put("filter", "b");
        a.getChildren().add(node("c", "text"));

        DashboardDraftState draft = new DashboardDraftState();
        draft.setComponents(new java.util.ArrayList<>(List.of(a, b)));
        draft.setDataSources(new java.util.LinkedHashMap<>(Map.of("sales", ds("sales"))));

        assertThat(validator.validate(draft)).isEmpty();
    }

    @Test
    void detectsMissingComponentReferenceWithPath() {
        ComponentNode a = node("a", "chart");
        a.getComponentRefs().put("filter", "ghost");
        DashboardDraftState draft = new DashboardDraftState();
        draft.setComponents(new java.util.ArrayList<>(List.of(a)));

        List<ValidationIssue> issues = validator.validate(draft);

        assertThat(issues).hasSize(1);
        ValidationIssue issue = issues.get(0);
        assertThat(issue.code()).isEqualTo("MISSING_COMPONENT_REFERENCE");
        assertThat(issue.path()).isEqualTo("components[0].componentRefs.filter");
        assertThat(issue.message()).contains("ghost");
    }

    @Test
    void detectsMissingDataSourceBinding() {
        ComponentNode a = node("a", "chart");
        a.getDataBindings().put("series", "nope");
        DashboardDraftState draft = new DashboardDraftState();
        draft.setComponents(new java.util.ArrayList<>(List.of(a)));

        List<ValidationIssue> issues = validator.validate(draft);

        assertThat(issues).extracting(ValidationIssue::code)
                .containsExactly("MISSING_DATA_SOURCE_REFERENCE");
        assertThat(issues.get(0).path()).isEqualTo("components[0].dataBindings.series");
    }

    @Test
    void detectsDirectCircularReference() {
        ComponentNode a = node("a", "chart");
        ComponentNode b = node("b", "filter");
        a.getComponentRefs().put("next", "b");
        b.getComponentRefs().put("prev", "a");
        DashboardDraftState draft = new DashboardDraftState();
        draft.setComponents(new java.util.ArrayList<>(List.of(a, b)));

        List<ValidationIssue> issues = validator.validate(draft);

        assertThat(issues).extracting(ValidationIssue::code).contains("CIRCULAR_REFERENCE");
        assertThat(issues).anyMatch(i -> i.message().contains("a -> b -> a")
                || i.message().contains("b -> a -> b"));
    }

    @Test
    void detectsSelfReference() {
        ComponentNode a = node("a", "chart");
        a.getComponentRefs().put("self", "a");
        DashboardDraftState draft = new DashboardDraftState();
        draft.setComponents(new java.util.ArrayList<>(List.of(a)));

        assertThat(validator.validate(draft)).extracting(ValidationIssue::code)
                .containsExactly("CIRCULAR_REFERENCE");
    }

    @Test
    void detectsStructuralProblems() {
        ComponentNode bad = node("", "");
        ComponentNode dup1 = node("x", "text");
        ComponentNode dup2 = node("x", "text");
        DashboardDraftState draft = new DashboardDraftState();
        draft.setComponents(new java.util.ArrayList<>(List.of(bad, dup1, dup2)));

        List<ValidationIssue> issues = validator.validate(draft);
        assertThat(issues).extracting(ValidationIssue::code)
                .contains("STRUCTURAL_ERROR", "STRUCTURAL_ERROR");
        assertThat(issues).anyMatch(i -> i.message().contains("Duplicate component id"));
    }
}
