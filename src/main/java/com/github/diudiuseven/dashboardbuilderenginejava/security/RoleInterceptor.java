package com.github.diudiuseven.dashboardbuilderenginejava.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.Role;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ForbiddenException;

/**
 * Enforces {@link RoleRequired} on controller methods.
 *
 * The caller's role is supplied with the {@code X-User-Role} header
 * (VIEWER / EDITOR / PUBLISHER). A missing or unknown role is treated as
 * unauthenticated and rejected. Authorization is checked before the handler
 * runs, so rejected requests never reach service code and never mutate state.
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    public static final String ROLE_HEADER = "X-User-Role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Role role = resolveRole(request.getHeader(ROLE_HEADER));
        if (role == null) {
            writeForbidden(response, "Missing or invalid '" + ROLE_HEADER + "' header");
            return false;
        }
        if (handler instanceof HandlerMethod handlerMethod) {
            RoleRequired required = handlerMethod.getMethodAnnotation(RoleRequired.class);
            if (required != null && role.ordinal() < required.value().ordinal()) {
                writeForbidden(response, "Role " + role + " is not permitted; "
                        + required.value() + " or higher is required");
                return false;
            }
        }
        CurrentRole.set(role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentRole.clear();
    }

    private Role resolveRole(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(header.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void writeForbidden(HttpServletResponse response, String message) throws java.io.IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\""
                + message.replace("\"", "'") + "\",\"issues\":[]}");
    }
}
