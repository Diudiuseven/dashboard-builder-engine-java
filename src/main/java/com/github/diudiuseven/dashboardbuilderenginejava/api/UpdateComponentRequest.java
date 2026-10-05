package com.github.diudiuseven.dashboardbuilderenginejava.api;

import java.util.Map;

/**
 * Patch a component. Null maps mean "leave unchanged".
 */
public record UpdateComponentRequest(String name,
                                    String type,
                                    Map<String, Object> props,
                                    Map<String, String> dataBindings,
                                    Map<String, String> componentRefs,
                                    long expectedEditVersion) {
}
