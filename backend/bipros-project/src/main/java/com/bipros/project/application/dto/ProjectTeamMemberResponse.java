package com.bipros.project.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectTeamMemberResponse(
        UUID id,
        UUID projectId,
        UUID userId,
        String role,
        UUID reportsToUserId,
        LocalDate activeFrom,
        LocalDate activeTo,
        Instant createdAt,
        Instant updatedAt,
        // Display projection from public.users — the Team tab renders these directly
        // instead of depending on a separate (role-filtered) user-directory lookup.
        String username,
        String firstName,
        String lastName,
        String email,
        String reportsToUsername,
        String reportsToName
) {
}
