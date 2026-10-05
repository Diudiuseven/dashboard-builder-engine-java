package com.github.diudiuseven.dashboardbuilderenginejava.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源注册表：只有注册过的数据源 id 才允许被组件绑定。
 */
@Component
public class DataSourceRegistry {

    private final Set<String> dataSourceIds = ConcurrentHashMap.newKeySet();

    public DataSourceRegistry() {
        // 预置示例数据源
        dataSourceIds.addAll(Set.of("ds-sales", "ds-traffic", "ds-user"));
    }

    public boolean exists(String dataSourceId) {
        return dataSourceIds.contains(dataSourceId);
    }

    public void register(String dataSourceId) {
        dataSourceIds.add(dataSourceId);
    }

    public Set<String> all() {
        return Set.copyOf(dataSourceIds);
    }
}
