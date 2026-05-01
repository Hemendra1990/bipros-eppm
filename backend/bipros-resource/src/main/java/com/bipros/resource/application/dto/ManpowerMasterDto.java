package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.enums.EmploymentType;
import com.bipros.resource.domain.model.enums.ManpowerCategory;
import com.bipros.resource.domain.model.manpower.ManpowerMaster;

import java.time.LocalDate;
import java.util.UUID;

public record ManpowerMasterDto(
    String employeeCode,
    String firstName,
    String lastName,
    String fullName,
    ManpowerCategory category,
    String subCategory,
    LocalDate dateOfBirth,
    String gender,
    String nationality,
    String contactNumber,
    String email,
    String address,
    String emergencyContact,
    String photoUrl,
    EmploymentType employmentType,
    String designation,
    String department,
    LocalDate joiningDate,
    LocalDate exitDate,
    UUID reportingManagerId,
    String companyName,
    String workLocation
) {

  public static ManpowerMasterDto from(ManpowerMaster m) {
    if (m == null) return null;
    return new ManpowerMasterDto(
        m.getEmployeeCode(),
        m.getFirstName(),
        m.getLastName(),
        m.getFullName(),
        m.getCategory(),
        m.getSubCategory(),
        m.getDateOfBirth(),
        m.getGender(),
        m.getNationality(),
        m.getContactNumber(),
        m.getEmail(),
        m.getAddress(),
        m.getEmergencyContact(),
        m.getPhotoUrl(),
        m.getEmploymentType(),
        m.getDesignation(),
        m.getDepartment(),
        m.getJoiningDate(),
        m.getExitDate(),
        m.getReportingManagerId(),
        m.getCompanyName(),
        m.getWorkLocation());
  }
}
