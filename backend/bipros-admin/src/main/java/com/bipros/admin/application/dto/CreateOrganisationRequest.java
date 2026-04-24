package com.bipros.admin.application.dto;

import com.bipros.admin.domain.model.OrganisationRegistrationStatus;
import com.bipros.admin.domain.model.OrganisationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create / update payload for the PMS MasterData Contractor Master (Screen 02). Field regex
 * mirrors the standard Indian PAN / GSTIN / mobile / pincode formats so invalid tax identifiers
 * are rejected at the edge.
 */
public record CreateOrganisationRequest(
    /** Leave blank to have the server auto-generate as CONT-NNN. */
    @Size(max = 30) String code,

    @NotBlank(message = "Name is required")
    @Size(max = 200) String name,

    @Size(max = 50) String shortName,

    @NotNull(message = "Organisation type is required")
    OrganisationType organisationType,

    UUID parentOrganisationId,

    Boolean active,

    @Size(max = 100) String contactPersonName,

    @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$",
        message = "Mobile must be 7-20 digits, may start with + and include hyphens/spaces")
    String contactMobile,

    @Email(message = "Invalid email format")
    @Size(max = 100) String contactEmail,

    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$",
        message = "PAN must match AAAAA0000A format")
    String pan,

    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$",
        message = "GSTIN must be 15 chars matching the standard GST format")
    String gstin,

    @Size(max = 30) String registrationNumber,

    @Size(max = 300) String addressLine,
    @Size(max = 80) String city,
    @Size(max = 80) String state,

    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be 6 digits")
    String pincode,

    OrganisationRegistrationStatus registrationStatus,

    /** Optional list of project IDs to link to on create. On update, replaces the existing list. */
    List<UUID> associatedProjectIds
) {
}
