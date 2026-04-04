package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateResourceRequest(
    @NotBlank(message = "Code is required") String code,
    @NotBlank(message = "Name is required") String name,
    @NotNull(message = "Resource type is required") ResourceType resourceType,
    UUID parentId,
    UUID calendarId,
    String email,
    String phone,
    String title,
    Double maxUnitsPerDay) {}
