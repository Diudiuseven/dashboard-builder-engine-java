package com.github.diudiuseven.dashboardbuilderenginejava.api;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.DataSourceDefinition;

public record DataSourceRequest(DataSourceDefinition dataSource, long expectedEditVersion) {
}
