package com.bipros.resource.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddToPoolRequest(
    @NotEmpty(message = "At least one pool entry is required")
    @Valid
    List<PoolEntryInput> entries
) {}
