package com.github.diudiuseven.dashboardbuilderenginejava.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single node in the dashboard component tree.
 */
public class ComponentNode {

    private String id;
    private String type;
    private String name;
    private Map<String, Object> props = new LinkedHashMap<>();
    /** prop name -> data source id referenced by this component */
    private Map<String, String> dataBindings = new LinkedHashMap<>();
    /** prop name -> id of another component referenced by this component */
    private Map<String, String> componentRefs = new LinkedHashMap<>();
    private List<ComponentNode> children = new ArrayList<>();

    public ComponentNode() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Object> getProps() { return props; }
    public void setProps(Map<String, Object> props) { this.props = props; }
    public Map<String, String> getDataBindings() { return dataBindings; }
    public void setDataBindings(Map<String, String> dataBindings) { this.dataBindings = dataBindings; }
    public Map<String, String> getComponentRefs() { return componentRefs; }
    public void setComponentRefs(Map<String, String> componentRefs) { this.componentRefs = componentRefs; }
    public List<ComponentNode> getChildren() { return children; }
    public void setChildren(List<ComponentNode> children) { this.children = children; }
}
