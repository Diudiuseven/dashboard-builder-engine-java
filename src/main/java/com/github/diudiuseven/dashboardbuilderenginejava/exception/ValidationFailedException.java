package com.github.diudiuseven.dashboardbuilderenginejava.exception;

import com.github.diudiuseven.dashboardbuilderenginejava.model.ValidationError;
import org.springframework.http.HttpStatus;

import java.util.List;

/** 校验失败，携带全部可定位错误。 */
public class ValidationFailedException extends ApiException {

    private final List<ValidationError> errors;

    public ValidationFailedException(List<ValidationError> errors) {
        super("validation failed: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
