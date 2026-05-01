package com.bipros.resource.domain.model.manpower;

import com.bipros.resource.domain.model.enums.MedicalStatus;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "manpower_compliance", schema = "resource")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerCompliance {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "id_proof_type", length = 40)
  private String idProofType;

  @Column(name = "id_proof_number", length = 60)
  private String idProofNumber;

  @Column(name = "labor_license_number", length = 60)
  private String laborLicenseNumber;

  @Column(name = "insurance_provider", length = 120)
  private String insuranceProvider;

  @Column(name = "insurance_policy_number", length = 60)
  private String insurancePolicyNumber;

  @Column(name = "insurance_expiry")
  private LocalDate insuranceExpiry;

  @Enumerated(EnumType.STRING)
  @Column(name = "medical_fitness_status", length = 20)
  private MedicalStatus medicalFitnessStatus;

  @Column(name = "medical_expiry")
  private LocalDate medicalExpiry;

  @Column(name = "safety_training_completed")
  private Boolean safetyTrainingCompleted;

  @Column(name = "safety_training_date")
  private LocalDate safetyTrainingDate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "compliance_certificates", columnDefinition = "jsonb")
  private String complianceCertificates;

  @Column(name = "resume_url", length = 500)
  private String resumeUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "certification_documents", columnDefinition = "jsonb")
  private String certificationDocuments;

  @Column(name = "contract_document_url", length = 500)
  private String contractDocumentUrl;

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
