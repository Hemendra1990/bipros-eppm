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

    // ── PMS MasterData Screen 02 (Contractor Master) fields ───────────────────

    /** Primary contact person for project communications. */
    @Column(name = "contact_person_name", length = 100)
    private String contactPersonName;

    /** Primary contact mobile (with country code). */
    @Column(name = "contact_mobile", length = 20)
    private String contactMobile;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    /** PAN (Permanent Account Number) — format AAAAA0000A. */
    @Column(name = "pan", length = 10)
    private String pan;

    /** GSTIN (Goods and Services Tax Identification Number) — 15-char format. */
    @Column(name = "gstin", length = 15)
    private String gstin;

    /** MCA/CIN or firm registration number. */
    @Column(name = "registration_number", length = 30)
    private String registrationNumber;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "state", length = 80)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    /**
     * Registration lifecycle status. Distinct from the legacy {@link #active} boolean so the UI
     * can disambiguate ACTIVE vs PENDING_KYC. The service keeps {@link #active} in sync
     * (CLOSED/SUSPENDED → {@code active = false}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", length = 20)
    @Default
    private OrganisationRegistrationStatus registrationStatus = OrganisationRegistrationStatus.ACTIVE;
}
