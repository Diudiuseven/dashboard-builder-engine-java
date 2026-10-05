package com.github.diudiuseven.dashboardbuilderenginejava.api;

import java.util.List;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.ValidationIssue;

/**
 * Uniform error body returned for all non-2xx responses.
 */
public record ApiError(String error, String message, List<ValidationIssue> issues) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, List.of());
    }
}
