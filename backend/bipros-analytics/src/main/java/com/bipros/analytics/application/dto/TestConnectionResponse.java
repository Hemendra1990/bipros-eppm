package com.bipros.analytics.application.dto;

import java.time.Instant;

public record TestConnectionResponse(boolean ok, String message, Instant validatedAt) {}
