package com.github.diudiuseven.dashboardbuilderenginejava.security;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.Role;

/**
 * Holds the authenticated role for the current request thread.
 */
public final class CurrentRole {

    private static final ThreadLocal<Role> HOLDER = new ThreadLocal<>();

    private CurrentRole() {
    }

    public static void set(Role role) {
        HOLDER.set(role);
    }

    public static Role get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
