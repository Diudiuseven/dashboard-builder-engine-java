package com.github.diudiuseven.dashboardbuilderenginejava.api;

/**
 * Publish the current draft. failAfterSnapshot=true simulates a failure of a
 * downstream publish step after the snapshot has been built, so tests can
 * prove the live version never ends up in an intermediate state.
 */
public record PublishRequest(long expectedEditVersion, boolean failAfterSnapshot) {
}
