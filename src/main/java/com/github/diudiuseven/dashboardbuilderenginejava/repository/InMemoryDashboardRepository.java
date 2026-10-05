package com.github.diudiuseven.dashboardbuilderenginejava.repository;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.Dashboard;
import com.github.diudiuseven.dashboardbuilderenginejava.service.JsonDeepCopier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

/**
 * In-memory storage. Every save/get passes through a JSON deep copy so that
 * callers can never observe or mutate stored state through shared object
 * references (this is what keeps drafts and published versions isolated).
 */
@Repository
public class InMemoryDashboardRepository implements DashboardRepository {

    private final ConcurrentMap<String, Dashboard> store = new ConcurrentHashMap<>();
    private final JsonDeepCopier copier = new JsonDeepCopier();

    @Override
    public Dashboard save(Dashboard dashboard) {
        Dashboard detached = copier.copy(dashboard, Dashboard.class);
        store.put(detached.getId(), detached);
        return copier.copy(detached, Dashboard.class);
    }

    @Override
    public Optional<Dashboard> findById(String id) {
        return Optional.ofNullable(store.get(id))
                .map(d -> copier.copy(d, Dashboard.class));
    }

    @Override
    public List<Dashboard> findAll() {
        List<Dashboard> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(Dashboard::getId));
        return all.stream().map(d -> copier.copy(d, Dashboard.class)).toList();
    }
}
