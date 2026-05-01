package com.bipros.resource.domain.model.manpower;

import com.bipros.resource.domain.model.enums.SkillLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  @Column(name = "primary_skill", length = 120)
  private String primarySkill;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "secondary_skills", columnDefinition = "jsonb")
  private String secondarySkills;

  @Enumerated(EnumType.STRING)
  @Column(name = "skill_level", length = 30)
  private SkillLevel skillLevel;

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
