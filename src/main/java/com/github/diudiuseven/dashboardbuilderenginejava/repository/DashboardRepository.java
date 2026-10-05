package com.github.diudiuseven.dashboardbuilderenginejava.repository;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.Dashboard;

import java.util.List;
import java.util.Optional;

/**
 * Storage for dashboards. Implementations must guarantee the returned
 * aggregates are isolated from stored state (deep copies on read/write),
 * so callers can never mutate a draft through a previously returned object.
 */
public interface DashboardRepository {

    Dashboard save(Dashboard dashboard);

    Optional<Dashboard> findById(String id);

    List<Dashboard> findAll();
}
