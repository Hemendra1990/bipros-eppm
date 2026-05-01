package com.bipros.resource.domain.model;

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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Equipment-specific 1:1 detail row keyed by the parent Resource id. Created lazily when an
 * EQUIPMENT-typed Resource is saved with equipment fields. {@code ON DELETE CASCADE} on the FK
 * means deleting the parent Resource cleans this up automatically.
 */
@Entity
@Table(name = "resource_equipment_details", schema = "resource")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceEquipmentDetails {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(length = 80)
  private String make;

  @Column(length = 80)
  private String model;

  @Column(length = 80)
  private String variant;

  @Column(name = "manufacturer_name", length = 120)
  private String manufacturerName;

  @Column(name = "country_of_origin", length = 60)
  private String countryOfOrigin;

  @Column(name = "year_of_manufacture")
  private Integer yearOfManufacture;

  @Column(name = "serial_number", length = 80)
  private String serialNumber;

  @Column(name = "chassis_number", length = 80)
  private String chassisNumber;

  @Column(name = "engine_number", length = 80)
  private String engineNumber;

  @Column(name = "registration_number", length = 80)
  private String registrationNumber;

  @Column(name = "capacity_spec", length = 80)
  private String capacitySpec;

  @Column(name = "fuel_litres_per_hour", precision = 10, scale = 2)
  private BigDecimal fuelLitresPerHour;

  @Column(name = "standard_output_per_day", precision = 15, scale = 4)
  private BigDecimal standardOutputPerDay;

  @Column(name = "standard_output_unit", length = 30)
  private String standardOutputUnit;

  @Enumerated(EnumType.STRING)
  @Column(name = "ownership_type", length = 30)
  private ResourceOwnership ownershipType;

  @Column(name = "quantity_available")
  private Integer quantityAvailable;

  @Column(name = "insurance_expiry")
  private LocalDate insuranceExpiry;

  @Column(name = "last_service_date")
  private LocalDate lastServiceDate;

  @Column(name = "next_service_date")
  private LocalDate nextServiceDate;

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
