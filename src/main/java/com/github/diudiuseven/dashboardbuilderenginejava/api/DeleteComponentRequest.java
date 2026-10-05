package com.github.diudiuseven.dashboardbuilderenginejava.api;

/**
 * Delete a component and its whole subtree. force=true additionally clears
 * references other components hold onto the deleted subtree.
 */
public record DeleteComponentRequest(long expectedEditVersion, boolean force) {
}
