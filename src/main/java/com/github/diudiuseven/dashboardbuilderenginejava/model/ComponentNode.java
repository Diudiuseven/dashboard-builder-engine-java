package com.github.diudiuseven.dashboardbuilderenginejava.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板中的一个组件节点，支持嵌套子组件。
 */
public class ComponentNode {

    private String id;
    private String type;
    private Map<String, Object> props = new LinkedHashMap<>();
    /** 该组件依赖的其他组件 id 列表（用于联动、引用校验）。 */
    private List<String> dependencies = new ArrayList<>();
    /** 该组件绑定的数据源 id，可为空。 */
    private String dataSourceId;
    private List<ComponentNode> children = new ArrayList<>();

    public ComponentNode() {
    }

    public ComponentNode(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props == null ? new LinkedHashMap<>() : props;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public List<ComponentNode> getChildren() {
        return children;
    }

    public void setChildren(List<ComponentNode> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }

    /** 深拷贝，保证版本快照之间互不共享引用。 */
    public ComponentNode deepCopy() {
        ComponentNode copy = new ComponentNode(id, type);
        copy.props = new LinkedHashMap<>(props);
        copy.dependencies = new ArrayList<>(dependencies);
        copy.dataSourceId = dataSourceId;
        for (ComponentNode child : children) {
            copy.children.add(child.deepCopy());
        }
        return copy;
    }
}
