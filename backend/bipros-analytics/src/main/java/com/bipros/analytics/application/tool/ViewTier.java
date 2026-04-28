package com.bipros.analytics.application.tool;

/**
 * Maps the caller's roles to the role-aware JsonView tier hierarchy
 * (com.bipros.common.web.json.Views). Highest applicable tier wins.
 */
public enum ViewTier {
    PUBLIC,
    INTERNAL,
    FINANCE_CONFIDENTIAL,
    ADMIN
}
