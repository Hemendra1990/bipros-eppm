package com.bipros.common.model;

import java.util.UUID;

/**
 * Marker interface for entities that participate in a tree hierarchy
 * (EPS, OBS, WBS, Cost Accounts, Resource Hierarchy).
 */
public interface HierarchyNode {

    UUID getId();

    UUID getParentId();

    String getName();

    String getCode();

    int getSortOrder();
}
