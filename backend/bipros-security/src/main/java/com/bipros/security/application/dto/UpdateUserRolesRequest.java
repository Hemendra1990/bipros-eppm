package com.bipros.security.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserRolesRequest(@NotNull List<String> roles) {}
