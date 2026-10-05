package com.github.diudiuseven.dashboardbuilderenginejava.api;

/**
 * Result of a successful state-changing request.
 */
public record ActionResponse(String status, String dashboardId,
                            long editVersion, Long publishedVersion) {

    public static ActionResponse edited(String dashboardId, long editVersion) {
        return new ActionResponse("OK", dashboardId, editVersion, null);
    }

    public static ActionResponse published(String dashboardId, long editVersion, long publishedVersion) {
        return new ActionResponse("PUBLISHED", dashboardId, editVersion, publishedVersion);
    }
}
