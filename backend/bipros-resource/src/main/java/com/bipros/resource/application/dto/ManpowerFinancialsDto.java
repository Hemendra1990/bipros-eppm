package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.enums.PaymentMode;
import com.bipros.resource.domain.model.manpower.ManpowerFinancials;

/**
 * HR/payroll record-keeping fields for a manpower resource. Intentionally does NOT carry the
 * project-cost rate — that lives on {@link com.bipros.resource.domain.model.Resource#getCostPerUnit()}.
 * Salary, allowances, deductions, PF/ESI/bank live here as reference data; project costing reads
 * only {@code Resource.costPerUnit}. Decoupled by design — matches Primavera P6 and other mature
 * PM tools where payroll and project-cost rates are separate concerns.
 */
public record ManpowerFinancialsDto(
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
