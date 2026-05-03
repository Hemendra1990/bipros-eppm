package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.resource.application.dto.ManpowerAllocationDto;
import com.bipros.resource.application.dto.ManpowerAttendanceDto;
import com.bipros.resource.application.dto.ManpowerComplianceDto;
import com.bipros.resource.application.dto.ManpowerDto;
import com.bipros.resource.application.dto.ManpowerFinancialsDto;
import com.bipros.resource.application.dto.ManpowerMasterDto;
import com.bipros.resource.application.dto.ManpowerSkillsDto;
import com.bipros.resource.domain.model.manpower.ManpowerAllocation;
import com.bipros.resource.domain.model.manpower.ManpowerAttendance;
import com.bipros.resource.domain.model.manpower.ManpowerCompliance;
import com.bipros.resource.domain.model.manpower.ManpowerFinancials;
import com.bipros.resource.domain.model.manpower.ManpowerMaster;
import com.bipros.resource.domain.model.manpower.ManpowerSkills;
import com.bipros.resource.domain.repository.ManpowerAllocationRepository;
import com.bipros.resource.domain.repository.ManpowerAttendanceRepository;
import com.bipros.resource.domain.repository.ManpowerComplianceRepository;
import com.bipros.resource.domain.repository.ManpowerFinancialsRepository;
import com.bipros.resource.domain.repository.ManpowerMasterRepository;
import com.bipros.resource.domain.repository.ManpowerSkillsRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ManpowerService {

  private final ManpowerMasterRepository masterRepository;
  private final ManpowerSkillsRepository skillsRepository;
  private final ManpowerFinancialsRepository financialsRepository;
  private final ManpowerAttendanceRepository attendanceRepository;
  private final ManpowerAllocationRepository allocationRepository;
  private final ManpowerComplianceRepository complianceRepository;
  private final ResourceRepository resourceRepository;

  @Transactional(readOnly = true)
  public ManpowerDto get(UUID resourceId) {
    return new ManpowerDto(
        masterRepository.findById(resourceId).map(ManpowerMasterDto::from).orElse(null),
        skillsRepository.findById(resourceId).map(ManpowerSkillsDto::from).orElse(null),
        financialsRepository.findById(resourceId).map(ManpowerFinancialsDto::from).orElse(null),
        attendanceRepository.findById(resourceId).map(ManpowerAttendanceDto::from).orElse(null),
        allocationRepository.findById(resourceId).map(ManpowerAllocationDto::from).orElse(null),
        complianceRepository.findById(resourceId).map(ManpowerComplianceDto::from).orElse(null));
  }

  public ManpowerDto upsertAll(UUID resourceId, ManpowerDto dto) {
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    if (dto.master() != null) upsertMaster(resourceId, dto.master());
    if (dto.skills() != null) upsertSkills(resourceId, dto.skills());
    if (dto.financials() != null) upsertFinancials(resourceId, dto.financials());
    if (dto.attendance() != null) upsertAttendance(resourceId, dto.attendance());
    if (dto.allocation() != null) upsertAllocation(resourceId, dto.allocation());
    if (dto.compliance() != null) upsertCompliance(resourceId, dto.compliance());
    return get(resourceId);
  }

  public ManpowerMasterDto upsertMaster(UUID resourceId, ManpowerMasterDto dto) {
    ManpowerMaster e = masterRepository.findById(resourceId).orElseGet(ManpowerMaster::new);
    e.setResourceId(resourceId);
    e.setEmployeeCode(dto.employeeCode());
    e.setFirstName(dto.firstName());
    e.setLastName(dto.lastName());
    e.setFullName(dto.fullName());
    e.setCategory(dto.category());
    e.setSubCategory(dto.subCategory());
    e.setDateOfBirth(dto.dateOfBirth());
    e.setGender(dto.gender());
    e.setNationality(dto.nationality());
    e.setContactNumber(dto.contactNumber());
    e.setEmail(dto.email());
    e.setAddress(dto.address());
    e.setEmergencyContact(dto.emergencyContact());
    e.setPhotoUrl(dto.photoUrl());
    e.setEmploymentType(dto.employmentType());
    e.setDesignation(dto.designation());
    e.setDepartment(dto.department());
    e.setJoiningDate(dto.joiningDate());
    e.setExitDate(dto.exitDate());
    e.setReportingManagerId(dto.reportingManagerId());
    e.setCompanyName(dto.companyName());
    e.setWorkLocation(dto.workLocation());
    return ManpowerMasterDto.from(masterRepository.save(e));
  }

  public ManpowerSkillsDto upsertSkills(UUID resourceId, ManpowerSkillsDto dto) {
    ManpowerSkills e = skillsRepository.findById(resourceId).orElseGet(ManpowerSkills::new);
    e.setResourceId(resourceId);
    e.setPrimarySkill(dto.primarySkill());
    e.setSecondarySkills(dto.secondarySkills());
    e.setSkillLevel(dto.skillLevel());
    e.setCertifications(dto.certifications());
    e.setLicenseDetails(dto.licenseDetails());
    e.setExperienceYears(dto.experienceYears());
    e.setTrainingRecords(dto.trainingRecords());
    return ManpowerSkillsDto.from(skillsRepository.save(e));
  }

  public ManpowerFinancialsDto upsertFinancials(UUID resourceId, ManpowerFinancialsDto dto) {
    ManpowerFinancials e = financialsRepository.findById(resourceId).orElseGet(ManpowerFinancials::new);
    e.setResourceId(resourceId);
    // salaryType / baseSalary / hourlyRate / overtimeRate columns intentionally not surfaced —
    // project costing uses Resource.costPerUnit, payroll details live in HR fields below.
    e.setAllowances(dto.allowances());
    e.setDeductions(dto.deductions());
    e.setCurrency(dto.currency());
    e.setBankAccountDetails(dto.bankAccountDetails());
    e.setPaymentMode(dto.paymentMode());
    e.setTaxDetails(dto.taxDetails());
    e.setPfNumber(dto.pfNumber());
    e.setEsiNumber(dto.esiNumber());
    return ManpowerFinancialsDto.from(financialsRepository.save(e));
  }

  public ManpowerAttendanceDto upsertAttendance(UUID resourceId, ManpowerAttendanceDto dto) {
    ManpowerAttendance e = attendanceRepository.findById(resourceId).orElseGet(ManpowerAttendance::new);
    e.setResourceId(resourceId);
    e.setDailyAttendanceStatus(dto.dailyAttendanceStatus());
    e.setLastCheckInTime(dto.lastCheckInTime());
    e.setLastCheckOutTime(dto.lastCheckOutTime());
    e.setWorkingHoursPerDay(dto.workingHoursPerDay());
    e.setShiftType(dto.shiftType());
    e.setTotalWorkHoursMtd(dto.totalWorkHoursMtd());
    e.setOvertimeHoursMtd(dto.overtimeHoursMtd());
    e.setLeaveBalance(dto.leaveBalance());
    e.setLeaveSchedule(dto.leaveSchedule());
    return ManpowerAttendanceDto.from(attendanceRepository.save(e));
  }

  public ManpowerAllocationDto upsertAllocation(UUID resourceId, ManpowerAllocationDto dto) {
    ManpowerAllocation e = allocationRepository.findById(resourceId).orElseGet(ManpowerAllocation::new);
    e.setResourceId(resourceId);
    e.setAvailabilityStatus(dto.availabilityStatus());
    e.setCurrentProjectId(dto.currentProjectId());
    e.setAssignedActivityId(dto.assignedActivityId());
    e.setSiteName(dto.siteName());
    e.setRoleInProject(dto.roleInProject());
    e.setCrewId(dto.crewId());
    e.setUtilizationPercentage(dto.utilizationPercentage());
    e.setStandardOutputPerHour(dto.standardOutputPerHour());
    e.setOutputUnit(dto.outputUnit());
    e.setEfficiencyFactor(dto.efficiencyFactor());
    e.setPerformanceRating(dto.performanceRating());
    e.setProductivityTrend(dto.productivityTrend());
    e.setAttritionRiskScore(dto.attritionRiskScore());
    e.setSkillGapAnalysis(dto.skillGapAnalysis());
    e.setRecommendedTraining(dto.recommendedTraining());
    e.setOptimalAssignment(dto.optimalAssignment());
    return ManpowerAllocationDto.from(allocationRepository.save(e));
  }

  public ManpowerComplianceDto upsertCompliance(UUID resourceId, ManpowerComplianceDto dto) {
    ManpowerCompliance e = complianceRepository.findById(resourceId).orElseGet(ManpowerCompliance::new);
    e.setResourceId(resourceId);
    e.setIdProofType(dto.idProofType());
    e.setIdProofNumber(dto.idProofNumber());
    e.setLaborLicenseNumber(dto.laborLicenseNumber());
    e.setInsuranceProvider(dto.insuranceProvider());
    e.setInsurancePolicyNumber(dto.insurancePolicyNumber());
    e.setInsuranceExpiry(dto.insuranceExpiry());
    e.setMedicalFitnessStatus(dto.medicalFitnessStatus());
    e.setMedicalExpiry(dto.medicalExpiry());
    e.setSafetyTrainingCompleted(dto.safetyTrainingCompleted());
    e.setSafetyTrainingDate(dto.safetyTrainingDate());
    e.setComplianceCertificates(dto.complianceCertificates());
    e.setResumeUrl(dto.resumeUrl());
    e.setCertificationDocuments(dto.certificationDocuments());
    e.setContractDocumentUrl(dto.contractDocumentUrl());
    return ManpowerComplianceDto.from(complianceRepository.save(e));
  }

  public void delete(UUID resourceId) {
    masterRepository.deleteById(resourceId);
    skillsRepository.deleteById(resourceId);
    financialsRepository.deleteById(resourceId);
    attendanceRepository.deleteById(resourceId);
    allocationRepository.deleteById(resourceId);
    complianceRepository.deleteById(resourceId);
  }
}
