package com.github.diudiuseven.dashboardbuilderenginejava.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A data source that components may bind to.
 */
public class DataSourceDefinition {

    private String id;
    private String name;
    private String type;
    private Map<String, Object> config = new LinkedHashMap<>();

    public DataSourceDefinition() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
}
