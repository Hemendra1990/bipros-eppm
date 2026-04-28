package com.bipros.analytics.application.dto;

import java.util.UUID;

/**
 * One row of the "top users by query count" panel on the admin health page.
 * Username resolution is left to the frontend (which already has a /v1/users API).
 */
public record TopUserRow(UUID userId, Long queryCount) {}
