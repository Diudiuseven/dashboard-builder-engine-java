package com.github.diudiuseven.dashboardbuilderenginejava.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mutable draft of a dashboard: its component tree, data sources
 * and an optimistic-lock version used to detect concurrent edits.
 */
public class DashboardDraftState {

    private List<ComponentNode> components = new ArrayList<>();
    private Map<String, DataSourceDefinition> dataSources = new LinkedHashMap<>();
    /** optimistic lock version, bumped on every successful draft mutation */
    private long editVersion = 0;

    public List<ComponentNode> getComponents() { return components; }
    public void setComponents(List<ComponentNode> components) { this.components = components; }
    public Map<String, DataSourceDefinition> getDataSources() { return dataSources; }
    public void setDataSources(Map<String, DataSourceDefinition> dataSources) { this.dataSources = dataSources; }
    public long getEditVersion() { return editVersion; }
    public void setEditVersion(long editVersion) { this.editVersion = editVersion; }
}
