package com.bipros.security.application.dto;

import com.bipros.security.domain.model.Department;
import com.bipros.security.domain.model.PresenceStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial-update payload for the Personnel Master (Screen 07). All fields optional — null
 * leaves the current value untouched. Email / mobile are validated when supplied.
 */
public record UpdateUserProfileRequest(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Email(message = "Invalid email format")
    @Size(max = 120) String email,

    /** +91 XXXXX-XXXXX or similar international format. */
    @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "Mobile must be 7-20 digits")
    String mobile,

    @Size(max = 120) String designation,
    Department department,

    UUID organisationId,

    LocalDate joiningDate,
    LocalDate contractEndDate,
    PresenceStatus presenceStatus,

    Boolean enabled
) {
}
