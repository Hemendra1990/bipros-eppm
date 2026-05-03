package com.bipros.security.application.dto;

import java.util.UUID;

/** profileId may be null to clear the assignment. */
public record AssignProfileRequest(UUID profileId) {}
