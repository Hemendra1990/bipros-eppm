package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.enums.MedicalStatus;
import com.bipros.resource.domain.model.manpower.ManpowerCompliance;

import java.time.LocalDate;

public record ManpowerComplianceDto(
    String idProofType,
    String idProofNumber,
    String laborLicenseNumber,
    String insuranceProvider,
    String insurancePolicyNumber,
    LocalDate insuranceExpiry,
    MedicalStatus medicalFitnessStatus,
    LocalDate medicalExpiry,
    Boolean safetyTrainingCompleted,
    LocalDate safetyTrainingDate,
    String complianceCertificates,
    String resumeUrl,
    String certificationDocuments,
    String contractDocumentUrl
) {

  public static ManpowerComplianceDto from(ManpowerCompliance c) {
    if (c == null) return null;
    return new ManpowerComplianceDto(
        c.getIdProofType(),
        c.getIdProofNumber(),
        c.getLaborLicenseNumber(),
        c.getInsuranceProvider(),
        c.getInsurancePolicyNumber(),
        c.getInsuranceExpiry(),
        c.getMedicalFitnessStatus(),
        c.getMedicalExpiry(),
        c.getSafetyTrainingCompleted(),
        c.getSafetyTrainingDate(),
        c.getComplianceCertificates(),
        c.getResumeUrl(),
        c.getCertificationDocuments(),
        c.getContractDocumentUrl());
  }
}
