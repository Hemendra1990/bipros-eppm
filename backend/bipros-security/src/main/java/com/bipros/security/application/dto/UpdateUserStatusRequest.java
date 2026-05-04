package com.bipros.security.application.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull Boolean enabled) {}
