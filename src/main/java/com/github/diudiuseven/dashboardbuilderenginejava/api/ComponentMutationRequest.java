package com.github.diudiuseven.dashboardbuilderenginejava.api;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.ComponentNode;

/**
 * Add a component. parentId == null means a root component.
 */
public record ComponentMutationRequest(ComponentNode component,
                                      String parentId,
                                      long expectedEditVersion) {
}
