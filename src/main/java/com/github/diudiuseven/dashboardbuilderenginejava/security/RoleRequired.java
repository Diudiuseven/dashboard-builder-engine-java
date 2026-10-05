package com.github.diudiuseven.dashboardbuilderenginejava.security;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Minimum role required to invoke a controller method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RoleRequired {
    Role value();
}
