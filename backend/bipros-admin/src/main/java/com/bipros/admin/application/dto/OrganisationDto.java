package com.bipros.admin.application.dto;

import com.bipros.admin.domain.model.OrganisationRegistrationStatus;
import com.bipros.admin.domain.model.OrganisationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganisationDto {
    private UUID id;
    private String code;
    private String name;
    private String shortName;
    private OrganisationType organisationType;
    private UUID parentOrganisationId;
    private boolean active;

    // ── PMS MasterData Screen 02 Contractor fields ──
    private String contactPersonName;
    private String contactMobile;
    private String contactEmail;
    private String pan;
    private String gstin;
    private String registrationNumber;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private OrganisationRegistrationStatus registrationStatus;

    /** Project IDs the organisation is linked to (from OrganisationProjectLink). */
    private List<UUID> associatedProjectIds;
}
