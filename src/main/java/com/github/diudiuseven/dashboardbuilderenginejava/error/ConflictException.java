package com.github.diudiuseven.dashboardbuilderenginejava.error;

/**
 * Raised on optimistic-lock conflicts (concurrent editors) or a concurrent
 * publish. Carries the current version so the client can reload and retry.
 */
public class ConflictException extends RuntimeException {

    private final long currentVersion;

    public ConflictException(String message, long currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
