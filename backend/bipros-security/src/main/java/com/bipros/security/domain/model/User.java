package com.bipros.security.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    /** IC-PMS: FK to organisations.id (nullable for legacy admin users). */
    @Column(name = "organisation_id")
    private UUID organisationId;

    /** IC-PMS: designation/title (e.g. "Additional Secretary", "Project Director"). */
    @Column(name = "designation", length = 120)
    private String designation;

    /** IC-PMS: primary role in the programme (e.g. "Employer – Director (PMO)"). */
    @Column(name = "primary_icpms_role", length = 120)
    private String primaryIcpmsRole;

    @ElementCollection(targetClass = AuthMethod.class, fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_auth_methods",
            schema = "public",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", length = 30)
    private Set<AuthMethod> authMethods = EnumSet.noneOf(AuthMethod.class);

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private Set<UserRole> roles = new HashSet<>();

    // ── PMS MasterData Screen 07 (Personnel Master) fields ───────────────────

    /** Auto-generated employee code {@code EMP-NNN}, unique per project. */
    @Column(name = "employee_code", length = 20, unique = true)
    private String employeeCode;

    /** Site contact number (format: {@code +91 XXXXX-XXXXX}). */
    @Column(name = "mobile", length = 20)
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", length = 20)
    private Department department;

    /** Date of joining the project. */
    @Column(name = "joining_date")
    private LocalDate joiningDate;

    /** Expected last date of assignment on this project. Alert raised when < 30 days remaining. */
    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    /** Current site-presence status (ON_SITE / ON_LEAVE / TRANSFERRED / RELEASED). */
    @Enumerated(EnumType.STRING)
    @Column(name = "presence_status", length = 20)
    private PresenceStatus presenceStatus;

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.accountLocked = false;
    }
}
