package com.github.diudiuseven.dashboardbuilderenginejava.domain;

import java.time.Instant;
import java.util.List;

/**
 * An immutable snapshot of a dashboard that may be rendered.
 */
public class PublishedVersion {

    private long version;
    private Instant publishedAt;
    private String publishedBy;
    /** version this one was restored from via rollback, if any */
    private Long restoredFromVersion;
    private List<ComponentNode> components;
    private java.util.Map<String, DataSourceDefinition> dataSources;

    public PublishedVersion() {
    }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public Long getRestoredFromVersion() { return restoredFromVersion; }
    public void setRestoredFromVersion(Long restoredFromVersion) { this.restoredFromVersion = restoredFromVersion; }
    public List<ComponentNode> getComponents() { return components; }
    public void setComponents(List<ComponentNode> components) { this.components = components; }
    public java.util.Map<String, DataSourceDefinition> getDataSources() { return dataSources; }
    public void setDataSources(java.util.Map<String, DataSourceDefinition> dataSources) { this.dataSources = dataSources; }
}
