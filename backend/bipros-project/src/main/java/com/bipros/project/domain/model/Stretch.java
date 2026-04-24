package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Chainage stretch per PMS MasterData Screen 06. A project's corridor is divided into one or
 * more stretches, each with its own assigned supervisor, package code, and milestone. Stretch
 * lengths are stored in metres and derived on save from (toChainageM − fromChainageM).
 */
@Entity
@Table(
    name = "stretch",
    schema = "project",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stretch_project_code",
            columnNames = {"project_id", "stretch_code"})
    },
    indexes = {
        @Index(name = "idx_stretch_project", columnList = "project_id"),
        @Index(name = "idx_stretch_status", columnList = "status"),
        @Index(name = "idx_stretch_supervisor", columnList = "assigned_supervisor_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stretch extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Stretch identifier auto-generated as {@code STR-NNN} per project. */
    @Column(name = "stretch_code", nullable = false, length = 30)
    private String stretchCode;

    /** Human-friendly name (e.g. "Zone A — km 145..149"). */
    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "from_chainage_m", nullable = false)
    private Long fromChainageM;

    @Column(name = "to_chainage_m", nullable = false)
    private Long toChainageM;

    /** Derived: {@code toChainageM − fromChainageM}. Recomputed on save by {@code StretchService}. */
    @Column(name = "length_m")
    private Long lengthM;

    /** FK into security.users — the supervisor responsible for this stretch. */
    @Column(name = "assigned_supervisor_id")
    private UUID assignedSupervisorId;

    /** Contract package code (e.g. "PKG-1A"). Matches {@code WbsNode.wbsPackageCode}. */
    @Column(name = "package_code", length = 60)
    private String packageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private StretchStatus status;

    @Column(name = "milestone_name", length = 200)
    private String milestoneName;

    @Column(name = "target_date")
    private LocalDate targetDate;
}
