package com.bipros.admin.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder.Default;

/**
 * IC-PMS Organisation master — NICDC, DMICDC, state SPVs, PMC firms, EPC contractors, auditors.
 * Sourced from the MasterData_OrgUsers sheet of the IC-PMS specification.
 */
@Entity
@Table(name = "organisations", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organisation extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "organisation_type", nullable = false, length = 40)
    private OrganisationType organisationType;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "parent_organisation_id")
    private java.util.UUID parentOrganisationId;

    @Column(name = "active", nullable = false)
    @Default
    private boolean active = true;
}
