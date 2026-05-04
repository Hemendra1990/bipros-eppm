package com.bipros.resource.domain.model.manpower;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manpower_skills", schema = "resource")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerSkills {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  // JSON-stringified array of skill names (e.g. '["Mason","Welder"]'), picked from SkillMaster.
  // Legacy single-value strings remain readable — the frontend wraps them as [value] on load.
  // Width set generously (500) to accommodate ~10 skill names without forcing a JSONB conversion.
  @Column(name = "primary_skill", length = 500)
  private String primarySkill;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "secondary_skills", columnDefinition = "jsonb")
  private String secondarySkills;

  // Stored as the master row's `name` (string) — picked from SkillLevelMaster.
  // Legacy enum strings ("BEGINNER" / "INTERMEDIATE" / "EXPERT") remain valid via seeded master rows.
  @Column(name = "skill_level", length = 120)
  private String skillLevel;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String certifications;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "license_details", columnDefinition = "jsonb")
  private String licenseDetails;

  @Column(name = "experience_years")
  private Integer experienceYears;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "training_records", columnDefinition = "jsonb")
  private String trainingRecords;

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
