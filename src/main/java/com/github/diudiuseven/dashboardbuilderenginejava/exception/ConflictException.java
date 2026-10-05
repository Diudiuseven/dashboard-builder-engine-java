package com.github.diudiuseven.dashboardbuilderenginejava.exception;

import org.springframework.http.HttpStatus;

/** 并发编辑冲突：操作基于的草稿版本已过期。 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
