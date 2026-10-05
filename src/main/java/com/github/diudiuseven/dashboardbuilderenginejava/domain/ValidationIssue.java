package com.github.diudiuseven.dashboardbuilderenginejava.domain;

/**
 * A single locatable validation problem.
 *
 * @param code     machine readable code, e.g. MISSING_COMPONENT_REFERENCE
 * @param path     JSON-ish path to the offending element, e.g. components[0].props.target
 * @param message  human readable explanation
 */
public record ValidationIssue(String code, String path, String message) {

    public static ValidationIssue missingComponent(String path, String referencedId) {
        return new ValidationIssue("MISSING_COMPONENT_REFERENCE", path,
                "Referenced component does not exist: " + referencedId);
    }

    public static ValidationIssue missingDataSource(String path, String referencedId) {
        return new ValidationIssue("MISSING_DATA_SOURCE_REFERENCE", path,
                "Referenced data source does not exist: " + referencedId);
    }

    public static ValidationIssue circularReference(String path, String cycle) {
        return new ValidationIssue("CIRCULAR_REFERENCE", path,
                "Circular component reference detected: " + cycle);
    }

    public static ValidationIssue structural(String path, String message) {
        return new ValidationIssue("STRUCTURAL_ERROR", path, message);
    }
}
