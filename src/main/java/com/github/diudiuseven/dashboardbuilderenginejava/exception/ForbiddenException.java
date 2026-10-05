package com.github.diudiuseven.dashboardbuilderenginejava.exception;

import org.springframework.http.HttpStatus;

/** 越权访问：当前角色无权执行该操作。 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
