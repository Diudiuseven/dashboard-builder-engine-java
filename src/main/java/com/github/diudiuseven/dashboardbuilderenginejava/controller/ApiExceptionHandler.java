package com.github.diudiuseven.dashboardbuilderenginejava.controller;

import com.github.diudiuseven.dashboardbuilderenginejava.exception.ApiException;
import com.github.diudiuseven.dashboardbuilderenginejava.exception.ValidationFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handle(ApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getClass().getSimpleName());
        body.put("message", ex.getMessage());
        if (ex instanceof ValidationFailedException vfe) {
            body.put("errors", vfe.getErrors());
        }
        return ResponseEntity.status(ex.status()).body(body);
    }
}
