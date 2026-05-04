package com.bipros.security.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A named bundle of fine-grained permissions ({@link PermissionCatalog} codes) that can be
 * assigned to a user. A user has at most one profile (current scope). Each profile also maps to
 * a single legacy {@link Role} name so existing {@code @PreAuthorize("hasRole(...)")} guards
 * stay correct while the platform migrates to permission-based enforcement.
 */
@Entity
@Table(name = "profiles", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Profile extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** {@code true} for the 10 shipped defaults — they cannot be deleted, only edited. */
    @Column(name = "system_default", nullable = false)
    private boolean systemDefault = false;

    /**
     * Name of the {@link Role} this profile maps to (for backward-compat with role-based guards).
     * When a user is assigned this profile, their {@code user_roles} row is set to this role.
     */
    @Column(name = "legacy_role_name", nullable = false, length = 60)
    private String legacyRoleName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "profile_permissions",
            schema = "public",
            joinColumns = @JoinColumn(name = "profile_id")
    )
    @Column(name = "permission_code", length = 80, nullable = false)
    private Set<String> permissions = new HashSet<>();

    public Profile(String code, String name, String description, String legacyRoleName,
                   boolean systemDefault, Set<String> permissions) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.legacyRoleName = legacyRoleName;
        this.systemDefault = systemDefault;
        this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
    }
}
