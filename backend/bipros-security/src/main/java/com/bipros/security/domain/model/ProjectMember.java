package com.bipros.security.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Maps a user to a single project with a project-scoped role
 * ({@link ProjectMemberRole#PROJECT_MANAGER PROJECT_MANAGER}, {@code SCHEDULER}, …).
 *
 * <p>One user can have multiple project_members rows for one project (e.g. PROJECT_MANAGER and
 * SCHEDULER) — the unique constraint includes {@code project_role}. Duplicates of the same
 * (user, project, role) triple are rejected.
 *
 * <p>Soft FKs only: {@code projectId} points into the {@code project} schema and {@code userId}
 * / {@code grantedBy} point into {@code public.users}, but no JPA relationships are declared so
 * this entity stays in {@code bipros-security} without dragging the {@code bipros-project}
 * dependency through every consumer. The DB-level FK is created by the Liquibase changeset.
 */
@Entity
@Table(
        name = "project_members",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_members_user_project_role",
                columnNames = {"user_id", "project_id", "project_role"}),
        indexes = {
                @Index(name = "idx_project_members_user", columnList = "user_id"),
                @Index(name = "idx_project_members_project", columnList = "project_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProjectMember extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false, length = 32)
    private ProjectMemberRole projectRole;

    @Column(name = "granted_by")
    private UUID grantedBy;
}
