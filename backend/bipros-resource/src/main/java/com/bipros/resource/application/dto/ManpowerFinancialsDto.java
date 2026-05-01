package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.enums.PaymentMode;
import com.bipros.resource.domain.model.enums.SalaryType;
import com.bipros.resource.domain.model.manpower.ManpowerFinancials;

import java.math.BigDecimal;

public record ManpowerFinancialsDto(
    SalaryType salaryType,
    BigDecimal baseSalary,
    BigDecimal hourlyRate,
    BigDecimal overtimeRate,
    String allowances,
    String deductions,
    String currency,
    String bankAccountDetails,
    PaymentMode paymentMode,
    String taxDetails,
    String pfNumber,
    String esiNumber
) {

  public static ManpowerFinancialsDto from(ManpowerFinancials f) {
    if (f == null) return null;
    return new ManpowerFinancialsDto(
        f.getSalaryType(),
        f.getBaseSalary(),
        f.getHourlyRate(),
        f.getOvertimeRate(),
        f.getAllowances(),
        f.getDeductions(),
        f.getCurrency(),
        f.getBankAccountDetails(),
        f.getPaymentMode(),
        f.getTaxDetails(),
        f.getPfNumber(),
        f.getEsiNumber());
  }
}
