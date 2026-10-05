package com.github.diudiuseven.dashboardbuilderenginejava.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A dashboard aggregate: one editable draft plus an append-only history
 * of published versions. The latest history entry is the live version.
 */
public class Dashboard {

    private String id;
    private String name;
    private DashboardDraftState draft = new DashboardDraftState();
    private List<PublishedVersion> publishedHistory = new ArrayList<>();

    public Dashboard() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DashboardDraftState getDraft() { return draft; }
    public void setDraft(DashboardDraftState draft) { this.draft = draft; }
    public List<PublishedVersion> getPublishedHistory() { return publishedHistory; }
    public void setPublishedHistory(List<PublishedVersion> publishedHistory) { this.publishedHistory = publishedHistory; }
}
