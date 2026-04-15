package com.bipros.admin.application.dto;

import com.bipros.admin.domain.model.OrganisationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
