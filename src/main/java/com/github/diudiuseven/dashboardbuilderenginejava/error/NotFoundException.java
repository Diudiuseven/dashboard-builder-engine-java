package com.github.diudiuseven.dashboardbuilderenginejava.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
