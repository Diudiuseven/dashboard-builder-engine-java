package com.github.diudiuseven.dashboardbuilderenginejava.web;

import com.github.diudiuseven.dashboardbuilderenginejava.api.ApiError;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ConflictException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ForbiddenException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.NotFoundException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.PublishFailureException;
import com.github.diudiuseven.dashboardbuilderenginejava.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> forbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        // Explain the conflict and return the current version so the client
        // can reload the draft and retry.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CONFLICT",
                "message", e.getMessage(),
                "currentEditVersion", e.getCurrentVersion()
        ));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> validation(ValidationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("VALIDATION_FAILED", e.getMessage(), e.getIssues()));
    }

    @ExceptionHandler(PublishFailureException.class)
    public ResponseEntity<ApiError> publishFailure(PublishFailureException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("PUBLISH_FAILED", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> badRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("BAD_REQUEST", "Malformed JSON request body"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("BAD_REQUEST", e.getMessage()));
    }
}
