package com.bipros.resource.domain.model.manpower;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "manpower_master",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_manpower_employee_code", columnNames = {"employee_code"})
    },
    indexes = {
        @Index(name = "idx_manpower_employee_code", columnList = "employee_code"),
        @Index(name = "idx_manpower_designation", columnList = "designation")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerMaster {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "employee_code", length = 50, unique = true)
  private String employeeCode;

  @Column(name = "first_name", length = 80)
  private String firstName;

  @Column(name = "last_name", length = 80)
  private String lastName;

  @Column(name = "full_name", length = 160)
  private String fullName;

  // Stored as the master row's `name` (string) — the form picks from ManpowerCategoryMaster
  // (top-level Categories where parent_id IS NULL). Legacy enum strings ("SKILLED" /
  // "UNSKILLED" / "STAFF") remain valid because the seeder creates master rows with those names.
  @Column(length = 120)
  private String category;

  // Stored as the master row's `name` (string) — picks from ManpowerCategoryMaster children of
  // the selected Category. Free-text fallback for any legacy values.
  @Column(name = "sub_category", length = 120)
  private String subCategory;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(length = 20)
  private String gender;

  @Column(length = 60)
  private String nationality;

  @Column(name = "contact_number", length = 30)
  private String contactNumber;

  @Column(length = 120)
  private String email;

  @Column(length = 500)
  private String address;

  @Column(name = "emergency_contact", length = 120)
  private String emergencyContact;

  @Column(name = "photo_url", length = 500)
  private String photoUrl;

  // Stored as the master row's `name` (string) — the form picks from EmploymentTypeMaster.
  // Legacy enum strings ("PERMANENT" / "CONTRACT" / "DAILY_WAGE") remain valid because the
  // seeder creates master rows with those names.
  @Column(name = "employment_type", length = 120)
  private String employmentType;

  @Column(length = 100)
  private String designation;

  @Column(length = 100)
  private String department;

  @Column(name = "joining_date")
  private LocalDate joiningDate;

  @Column(name = "exit_date")
  private LocalDate exitDate;

  @Column(name = "reporting_manager_id")
  private UUID reportingManagerId;

  @Column(name = "company_name", length = 120)
  private String companyName;

  @Column(name = "work_location", length = 120)
  private String workLocation;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @LastModifiedBy
  @Column(name = "updated_by")
  private String updatedBy;

  @Version
  @Column(name = "version")
  private Long version;
}
