package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Assignment of a security {@code User} to a resource-side {@code Role} (e.g. General Labourer,
 * Supervisor). Lets a single role have many users assigned (team of labourers, multiple masons, etc.)
 * while the role owns the costed day-rate used by the Daily Cost Report.
 *
 * <p>Distinct from {@code security.user_roles}, which maps a user to platform auth roles
 * (ADMIN / VIEWER / etc.). This mapping is construction-workforce metadata.
 */
@Entity
@Table(
    name = "user_resource_roles",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_resource_role",
            columnNames = {"user_id", "resource_role_id"})
    },
    indexes = {
        @Index(name = "idx_urr_user", columnList = "user_id"),
        @Index(name = "idx_urr_resource_role", columnList = "resource_role_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResourceRole extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "resource_role_id", nullable = false)
  private UUID resourceRoleId;

  @Column(name = "assigned_from")
  private LocalDate assignedFrom;

  /** Null = open-ended assignment (still active). */
  @Column(name = "assigned_to")
  private LocalDate assignedTo;

  @Column(name = "remarks", length = 500)
  private String remarks;
}
