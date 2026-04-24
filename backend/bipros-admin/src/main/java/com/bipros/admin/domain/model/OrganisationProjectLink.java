package com.bipros.admin.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Many-to-many link between an {@link Organisation} and a Project. Materialises the
 * "Associated Project" multi-select on the PMS MasterData Contractor Master screen so the UI
 * can list contractors engaged on a given project and vice versa.
 */
@Entity
@Table(
    name = "organisation_project_link",
    schema = "public",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_org_project",
        columnNames = {"organisation_id", "project_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganisationProjectLink extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Role the organisation plays on this project (e.g. PRIMARY_EPC, DESIGN_CONSULTANT). */
    @Column(name = "role_code", length = 40)
    private String roleCode;
}
