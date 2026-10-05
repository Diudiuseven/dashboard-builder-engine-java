package com.github.diudiuseven.dashboardbuilderenginejava.error;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.ValidationIssue;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<ValidationIssue> issues;

    public ValidationException(List<ValidationIssue> issues) {
        super("Draft validation failed with " + issues.size() + " issue(s)");
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }
}
