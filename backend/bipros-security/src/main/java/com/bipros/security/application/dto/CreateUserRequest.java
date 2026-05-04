package com.bipros.security.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 60) String username,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        UUID profileId,
        Boolean enabled
) {}
