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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-user, per-module access level entry (IC-PMS permission matrix).
 * Composite uniqueness on (userId, module) — enforced at DB level.
 */
@Entity
@Table(
        name = "user_module_access",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_module", columnNames = {"user_id", "module"}),
        indexes = @Index(name = "idx_user_module_access_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserModuleAccess extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 30)
    private IcpmsModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    private ModuleAccessLevel accessLevel;
}
