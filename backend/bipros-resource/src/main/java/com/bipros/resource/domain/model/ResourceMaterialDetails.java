package com.bipros.resource.domain.model;

import com.bipros.resource.domain.model.enums.MaterialType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Material-specific 1:1 detail row keyed by the parent Resource id. {@code alternateUnits} is
 * stored as JSONB; the field is typed as String so callers exchange raw JSON.
 */
@Entity
@Table(name = "resource_material_details", schema = "resource")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceMaterialDetails {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_type", length = 30)
  private MaterialType materialType;

  @Column(length = 80)
  private String category;

  @Column(name = "sub_category", length = 80)
  private String subCategory;

  @Column(name = "material_grade", length = 80)
  private String materialGrade;

  @Column(length = 500)
  private String specification;

  @Column(length = 80)
  private String brand;

  @Column(name = "manufacturer_name", length = 120)
  private String manufacturerName;

  @Column(name = "standard_code", length = 80)
  private String standardCode;

  @Column(name = "quality_class", length = 60)
  private String qualityClass;

  @Column(name = "base_unit", length = 30)
  private String baseUnit;

  @Column(name = "conversion_factor", precision = 15, scale = 6)
  private BigDecimal conversionFactor;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "alternate_units", columnDefinition = "jsonb")
  private String alternateUnits;

  @Column(precision = 10, scale = 4)
  private BigDecimal density;

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
