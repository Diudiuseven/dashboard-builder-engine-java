package com.github.diudiuseven.dashboardbuilderenginejava.model;

/**
 * 可定位的校验错误：包含出错组件 id 与可读描述。
 */
public record ValidationError(String componentId, String message) {

    @Override
    public String toString() {
        return "[" + componentId + "] " + message;
    }
}
