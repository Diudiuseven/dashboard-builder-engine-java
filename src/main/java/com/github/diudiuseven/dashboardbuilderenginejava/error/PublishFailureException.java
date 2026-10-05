package com.github.diudiuseven.dashboardbuilderenginejava.error;

/**
 * Raised when a publish step fails after pre-validation (e.g. a simulated
 * storage/render failure). The live version must remain untouched.
 */
public class PublishFailureException extends RuntimeException {
    public PublishFailureException(String message) {
        super(message);
    }
}
